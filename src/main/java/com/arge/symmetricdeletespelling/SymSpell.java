package com.arge.symmetricdeletespelling;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.arge.symmetricdeletespelling.DistanceCalc.damerauLevenshteinDistance;

/***
 * SymSpell: 1 million times faster through Symmetric Delete spelling correction algorithm
 *
 * The Symmetric Delete spelling correction algorithm reduces the complexity of edit candidate generation and dictionary lookup 
 * for a given Damerau-Levenshtein distance. It is six orders of magnitude faster and language independent.
 * Opposite to other algorithms only deletes are required, no transposes + replaces + inserts.
 * Transposes + replaces + inserts of the input term are transformed into deletes of the dictionary term.
 * Replaces and inserts are expensive and language dependent: e.g. Chinese has 70,000 Unicode Han characters!
 *
 * Copyright (C) 2015 Wolf Garbe
 * Version: 3.0
 * Author: Wolf Garbe <wolf.garbe@faroo.com>
 * Maintainer: Wolf Garbe <wolf.garbe@faroo.com>
 * URL: http://blog.faroo.com/2012/06/07/improved-edit-distance-based-spelling-correction/
 * Description: http://blog.faroo.com/2012/06/07/improved-edit-distance-based-spelling-correction/
 *
 * License:
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License, 
 * version 3.0 (LGPL-3.0) as published by the Free Software Foundation.
 * http://www.opensource.org/licenses/LGPL-3.0
 *
 * Usage: single word + Enter:  Display spelling suggestions
 *      Enter without input:  Terminate the program
 */
public class SymSpell {

  private static int maxlength = 0;
  private static int editDistanceMax = 2;
  //0: top suggestion
  //1: all suggestions of smallest edit distance 
  //2: all suggestions <= editDistanceMax (slower, no early termination)

  /**
   * Dictionary that contains both the original words and the deletes derived from them. A term might be both word and delete from another word at the same time.
   * For space reduction a item might be either of type DictionaryItem or Int.
   * A DictionaryItem is used for word, word/delete, and delete with multiple suggestions. Int is used for deletes with a single suggestion (the majority of entries).
   */
  private static HashMap<String, Object> dictionary = new HashMap<>();

  /**
   * List of unique words. By using the suggestions (Int) as index for this list they are translated into the original String.
   */
  private static List<String> wordlist = new ArrayList<>();

  /**
   * create a non-unique wordlist from sample text
   * language independent (e.g. works with Chinese characters)
   * // \w Alphanumeric characters (including non-latin characters, umlaut characters and digits) plus "_"
   * // \d Digits
   * // Provides identical results to Norvigs regex "[a-z]+" for latin characters, while additionally providing compatibility with non-latin characters
   */
  private static Iterable<String> parseWords(String text) {
    List<String> allMatches = new ArrayList<>();
    Matcher m = Pattern.compile("[\\w-[\\d_]]+").matcher(text.toLowerCase());
    while (m.find()) {
      allMatches.add(m.group());
    }
    return allMatches;
  }

  /**
   * for every word there all deletes with an edit distance of 1..editDistanceMax created and added to the dictionary
   * every delete entry has a suggestions list, which points to the original term(s) it was created from
   * The dictionary may be dynamically updated (word frequency and new words) at any time by calling createDictionaryEntry
   */
  private static boolean createDictionaryEntry(String key) {
    boolean result = false;
    DictionaryItem dictionaryItem = null;
    Object valueObj;
    valueObj = dictionary.get(key);
    if (valueObj != null) {
      //int or DictionaryItem? delete existed before word!
      if (valueObj instanceof Integer) {
        int tmp = (Integer) valueObj;
        dictionaryItem = new DictionaryItem();
        dictionaryItem.getSuggestions().add(tmp);
        dictionary.put(key, dictionaryItem);
      } else {
        //already exists:
        //1. word appears several times
        //2. word1==deletes(word2)
        dictionaryItem = (DictionaryItem) valueObj;
      }

      //prevent overflow
      if (dictionaryItem.getCount() < Integer.MAX_VALUE) {
        dictionaryItem.setCount(dictionaryItem.getCount() + 1);
      }
    } else if (wordlist.size() < Integer.MAX_VALUE) {
      dictionaryItem = new DictionaryItem();
      dictionaryItem.setCount(dictionaryItem.getCount() + 1);
      dictionary.put(key, dictionaryItem);

      if (key.length() > maxlength) {
        maxlength = key.length();
      }
    }

    /*
     *  edits/suggestions are created only once, no matter how often word occurs
     *  edits/suggestions are created only as soon as the word occurs in the corpus,
     *  even if the same term existed before in the dictionary as an edit from another word
     *  a treshold might be specifid, when a term occurs so frequently in the corpus that it is considered a valid word for spelling correction
     */
    if (dictionaryItem != null && dictionaryItem.getCount() == 1) {
      //word2index
      wordlist.add(key);
      int keyint = wordlist.size() - 1;

      result = true;

      //create deletes
      for (String delete : edit(key, 0, new HashSet<>())) {
        Object value2;
        value2 = dictionary.get(delete);
        if (value2 != null) {
          if (value2 instanceof Integer) {
            //transformes int to DictionaryItem
            int tmp = (Integer) value2;
            DictionaryItem di = new DictionaryItem();
            di.getSuggestions().add(tmp);
            dictionary.put(delete, di);
            if (!di.getSuggestions().contains(keyint)) {
              addLowestDistance(di, key, keyint, delete);
            }
          } else if (!((DictionaryItem) value2).getSuggestions().contains(keyint)) {
            addLowestDistance((DictionaryItem) value2, key, keyint, delete);
          }
        } else {
          dictionary.put(delete, keyint);
        }

      }
    }
    return result;
  }

  //create a frequency dictionary from a corpus
  private static void createDictionary(String corpus) throws IOException {
    File f = new File(corpus);
    if (!(f.exists() && !f.isDirectory())) {
      System.out.println("File not found: " + corpus);
      return;
    }

    System.out.println("Creating dictionary ...");

    try (BufferedReader br = new BufferedReader(new FileReader(corpus))) {
      String line;
      while ((line = br.readLine()) != null) {
        for (String key : parseWords(line)) {
          createDictionaryEntry(key);
        }
      }
    }

    System.out.println("Dictionary created");
  }

  //save some time and space
  private static void addLowestDistance(DictionaryItem item, String suggestion, int suggestionint, String delete) {
    int deleteLength = delete.length();
    int suggestionLength = suggestion.length();
    if (!item.getSuggestions().isEmpty()) {
      if (wordlist.get(item.getSuggestions().get(0)).length() - deleteLength > suggestionLength - deleteLength) {
        item.getSuggestions().clear();
      }
    } else if (item.getSuggestions().isEmpty() || wordlist.get(item.getSuggestions().get(0)).length() - deleteLength >= suggestionLength - deleteLength) {
      item.getSuggestions().add(suggestionint);
    }
  }

  /* inexpensive and language independent: only deletes, no transposes + replaces + inserts
   * replaces and inserts are expensive and language dependent (Chinese has 70,000 Unicode Han characters)
   */
  private static HashSet<String> edit(String word, int editDistance, HashSet<String> deletes) {
    editDistance++;
    if (word.length() > 1) {
      for (int i = 0; i < word.length(); i++) {
        //delete ith character
        String delete = word.substring(0, i) + word.substring(i + 1);
        if (deletes.add(delete) && editDistance < editDistanceMax) {
          //recursion, if maximum edit distance not yet reached
          edit(delete, editDistance, deletes);
        }
      }
    }
    return deletes;
  }

  /**
   * check in dictionary for existence and frequency; sort by ascending edit distance, then by descending word frequency
   * /*
   * True Damerau-Levenshtein Edit Distance: adjust distance, if both distances>0
   * We allow simultaneous edits (deletes) of editDistanceMax on on both the dictionary and the input term.
   * For replaces and adjacent transposes the resulting edit distance stays <= editDistanceMax.
   * For inserts and deletes the resulting edit distance might exceed editDistanceMax.
   * To prevent suggestions of a higher edit distance, we need to calculate the resulting edit distance, if there are simultaneous edits on both sides.
   * Example: (bank==bnak and bank==bink, but bank!=kanb and bank!=xban and bank!=baxn for editDistanceMaxe=1)
   * Two deletes on each side of a pair makes them all equal, but the first two pairs have edit distance=1, the others edit distance=2.
   */

  private static List<SuggestItem> lookup(String input, String language, int editDistanceMax) {
    //save some time
    if (input.length() - editDistanceMax > maxlength) {
      return new ArrayList<>();
    }

    List<String> candidates = new ArrayList<>();
    HashSet<String> hashset1 = new HashSet<>();

    List<SuggestItem> suggestions = new ArrayList<>();
    HashSet<String> hashset2 = new HashSet<>();

    Object valueObject;

    //add original term
    candidates.add(input);

    while (!candidates.isEmpty()) {
      String candidate = candidates.get(0);
      candidates.remove(0);
      int candidateLength = candidate.length();
      if (!suggestions.isEmpty() && (input.length() - candidateLength > suggestions.get(0).getDistance())) {
        break;
      }

      //read candidate entry from dictionary
      valueObject = dictionary.get(language + candidate);
      if (valueObject != null) {
        DictionaryItem value = new DictionaryItem();
        if (valueObject instanceof Integer) {
          value.getSuggestions().add((Integer) valueObject);
        } else {
          value = (DictionaryItem) valueObject;
        }

        //if count>0 then candidate entry is correct dictionary term, not only delete item
        if ((value.getCount() > 0) && hashset2.add(candidate)) {
          //add correct dictionary term term to suggestion list
          SuggestItem si = new SuggestItem();
          si.setTerm(candidate);
          si.setCount(value.getCount());
          si.setDistance(input.length() - candidateLength);
          suggestions.add(si);
          //early termination
          if ((input.length() - candidateLength == 0)) {
            break;
          }
        }

        //iterate through suggestions (to other correct dictionary items) of delete item and add them to suggestion list
        Object valueObject2;
        for (int suggestionint : value.getSuggestions()) {
          //save some time
          //skipping double items early: different deletes of the input term can lead to the same suggestion
          //index2word
          //TODO
          String suggestion = wordlist.get(suggestionint);
          if (hashset2.add(suggestion)) {
            int distance = 0;
            if (!suggestion.equals(input)) {
              if (suggestion.length() == candidateLength) {
                distance = input.length() - candidateLength;
              } else if (input.length() == candidateLength) {
                distance = suggestion.length() - candidateLength;
              } else {
                //common prefixes and suffixes are ignored, because this speeds up the Damerau-levenshtein-Distance calculation without changing it.
                int ii = 0;
                int jj = 0;
                while ((ii < suggestion.length()) && (ii < input.length()) && (suggestion.charAt(ii) == input.charAt(ii))) {
                  ii++;
                }
                while ((jj < suggestion.length() - ii) && (jj < input.length() - ii) && (suggestion.charAt(suggestion.length() - jj - 1) == input.charAt(input.length() - jj - 1))) {
                  jj++;
                }
                if (ii > 0 || jj > 0) {
                  distance = damerauLevenshteinDistance(suggestion.substring(ii, suggestion.length() - jj), input.substring(ii, input.length() - jj));
                } else {
                  distance = damerauLevenshteinDistance(suggestion, input);
                }
              }
            }

            //save some time.
            if (!suggestions.isEmpty() && (suggestions.get(0).getDistance() > distance)) {
              suggestions.clear();
            }
            //do not process higher distances than those already found, if verbose<2
            if (!suggestions.isEmpty() && (distance > suggestions.get(0).getDistance())) {
              continue;
            }

            if (distance <= editDistanceMax) {
              valueObject2 = dictionary.get(language + suggestion);
              if (valueObject2 != null) {
                SuggestItem si = new SuggestItem();
                si.setTerm(suggestion);
                si.setCount(((DictionaryItem) valueObject2).getCount());
                si.setDistance(distance);
                suggestions.add(si);
              }
            }
          }
        }//end foreach
      }//end if

      //add edits
      //derive edits (deletes) from candidate (input) and add them to candidates list
      //this is a recursive process until the maximum edit distance has been reached
      if (input.length() - candidateLength < editDistanceMax) {
        //save some time
        //do not create edits with edit distance smaller than suggestions already found
        if (!suggestions.isEmpty() && (input.length() - candidateLength >= suggestions.get(0).getDistance())) {
          continue;
        }

        for (int i = 0; i < candidateLength; i++) {
          String delete = candidate.substring(0, i) + candidate.substring(i + 1);
          if (hashset1.add(delete)) {
            candidates.add(delete);
          }
        }
      }
    } //end while

    sortAscDistanceAndDescFreq(suggestions);

    if (suggestions.size() > 1) {
      return suggestions.subList(0, 1);
    } else {
      return suggestions;
    }
  }


  /**
   * sort by ascending edit distance, then by descending word frequency
   */
  private static void sortAscDistanceAndDescFreq(List<SuggestItem> suggestions) {
    Collections.sort(suggestions, new Comparator<SuggestItem>() {
      public int compare(SuggestItem x, SuggestItem y) {
        return ((2 * x.getDistance() - y.getDistance()) > 0 ? 1 : 0) - ((x.getCount() - y.getCount()) > 0 ? 1 : 0);
      }
    });
  }

  private static void correct(String input, String language) {
    List<SuggestItem> suggestions = lookup(input, language, editDistanceMax);
    suggestions.forEach(item -> System.out.println("Suggestion: " + item.getTerm()));
  }

  private static void readFromStdIn() throws IOException {
    String word;
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    while ((word = br.readLine()) != null) {
      correct(word, "");
    }
  }

  /**
   * Create the dictionary from a sample corpus
   * e.g. http://norvig.com/big.txt , or any other large text corpus
   * The dictionary may contain vocabulary from different languages.
   * If you use mixed vocabulary use the language parameter in correct() and createDictionary() accordingly.
   * You may use createDictionaryEntry() to update a (self learning) dictionary incrementally
   * To extend spelling correction beyond single words to phrases (e.g. correcting "unitedkingom" to "united kingdom") simply add those phrases with createDictionaryEntry().
   */
  public static void main(String[] args) throws IOException {
    createDictionary(getFileFromResources("referenceNamesAndQuarters50k.txt"));
    readFromStdIn();
  }

  private static String getFileFromResources(String fileName) {
//    return getClass().getClassLoader().getResource(fileName).getPath();
    return "E:\\projects\\github\\SymmetricDeleteSpelling\\src\\main\\resources\\" + fileName;
  }

}
