package com.arge.symmetricdeletespelling;


import java.util.ArrayList;
import java.util.List;

public class DictionaryItem {
  private List<Integer> suggestions = new ArrayList<Integer>();
  private int count = 0;

  public List<Integer> getSuggestions() {
    return suggestions;
  }

  public void setSuggestions(List<Integer> suggestions) {
    this.suggestions = suggestions;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }
}