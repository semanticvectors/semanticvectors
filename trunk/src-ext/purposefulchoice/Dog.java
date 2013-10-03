package purposefulchoice;

/**
 * Base class for modelling a dog in some position watching for stimuli trying to get food.
 * 
 * @author dwiddows
 */
public abstract class Dog {

  /** The maximum loss that is allowed in either coordinate when updating position. */ 
  public static final double MAX_LOSS = 0.01;
  
  protected double currentPointX;
  protected double currentPointY;
  
  double getCurrentPointX() {
    return currentPointX;
  }
  
  double getCurrentPointY() {
    return currentPointY;
  }
  
  public int foodWon;
  
  public Dog(int foodWon, double x, double y) {
    this.foodWon = foodWon;
    this.currentPointX = x;
    this.currentPointY = y;
  }
  
  abstract void pullToX();
  abstract void pullToY();
  
  @Override
  public String toString() {
    return String.format("Food Won: %d   x: %1.3f    y: %1.3f", foodWon, currentPointX, currentPointY);
  }
}
