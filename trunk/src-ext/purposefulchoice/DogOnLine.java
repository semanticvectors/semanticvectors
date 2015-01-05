package purposefulchoice;

/**
 * Dog whose attention vector uses Manhattan normalization.
 * 
 * @author dwiddows
 */
public class DogOnLine extends Dog {
  
  public DogOnLine(int foodWon, double x, double y) {
    super(foodWon, x, y);
  }

  @Override
  void pullToX() {
    currentPointY = currentPointY - MAX_LOSS;
    if (currentPointY < 0) currentPointY = 0;
    currentPointX = 1 - currentPointY; 
  }

  @Override
  void pullToY() {
    currentPointX = currentPointX - MAX_LOSS;
    if (currentPointX < 0) currentPointX = 0;
    currentPointY = 1 - currentPointX; 
  }
}