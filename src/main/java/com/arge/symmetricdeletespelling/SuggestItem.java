package com.arge.symmetricdeletespelling;

public class SuggestItem {

  private String term = "";
  private int distance = 0;
  private int count = 0;

  public String getTerm() {
    return term;
  }

  public void setTerm(String term) {
    this.term = term;
  }

  public int getDistance() {
    return distance;
  }

  public void setDistance(int distance) {
    this.distance = distance;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }


  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (this.getClass() != obj.getClass()) {
      return false;
    }
    return term.equals(((SuggestItem) obj).term);
  }

  @Override
  public int hashCode() {
    return term.hashCode();
  }
}

