package purposefulchoice;

/**
 * Dog whose attention vector uses Euclidean normalization.
 * 
 * @author dwiddows
 */
public class DogOnCurve extends Dog {
  public DogOnCurve(int foodWon, double x, double y) {
    super(foodWon, x, y);
  }

  public static final double RADIUS_SQUARED = 0.5; // 2 / Math.PI;

  @Override
  void pullToX() {
    currentPointY = currentPointY - MAX_LOSS;
    if (currentPointY < 0) currentPointY = 0;
    if (currentPointY * currentPointY > RADIUS_SQUARED) {
      currentPointX = 0;
    } else {
      currentPointX = Math.sqrt(RADIUS_SQUARED - (currentPointY * currentPointY));
    }
  }

  @Override
  void pullToY() {
    currentPointX = currentPointX - MAX_LOSS;
    if (currentPointX < 0) currentPointX = 0;
    if (currentPointX * currentPointX > RADIUS_SQUARED) {
      currentPointY = 0;
    } else {
      currentPointY = Math.sqrt(RADIUS_SQUARED - (currentPointX * currentPointX));
    }
  }
}
