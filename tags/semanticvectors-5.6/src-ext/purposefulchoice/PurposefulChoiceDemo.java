package purposefulchoice;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class PurposefulChoiceDemo extends JPanel {
  public static final int MAX_TURNS = 1000;

  DogOnLine dogOnLine;
  DogOnCurve dogOnCurve;
  Dog[] dogs;
  Random random;
  boolean foodAtX;

  public PurposefulChoiceDemo() {
    dogOnLine = new DogOnLine(0, 1, 0);
    dogOnCurve = new DogOnCurve(0, Math.sqrt(DogOnCurve.RADIUS_SQUARED), 0);
    dogs = new Dog[] { dogOnLine, dogOnCurve };
    random = new Random(6);
  }
  
  public void dropFood() {
    foodAtX = random.nextBoolean();
    if (foodAtX) {
      System.out.print("New X.   ");
      spotFoodX();
    } else {
      System.out.print("New Y.   ");
      spotFoodY();
    }
  }
  
  private void spotFoodX() {
    double nextRand = random.nextDouble();
    for (Dog dog : dogs) {
      if (nextRand <= dog.getCurrentPointX()) {
        ++dog.foodWon;
      }
      dog.pullToX();
    }
  }
  
  private void spotFoodY() {
    double nextRand = random.nextDouble();
    for (Dog dog : dogs) {
      if (nextRand <= dog.getCurrentPointY()) {
        ++dog.foodWon;
      }
      dog.pullToY();
    }
  }
  
  public String currentScores() {
    return String.format("%s   ||   %s", dogOnLine.toString(), dogOnCurve.toString());
  }
  
  final static float DASH_SIZE[] = {10.0f};
  final static BasicStroke DASHED_STROKE = new BasicStroke(
      1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, DASH_SIZE, 0.0f);
  
  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    this.removeAll();
    this.updateUI();
    g.setColor(Color.BLACK);
    g.drawLine(100, 500, 100, 100);
    g.drawLine(100, 500, 500, 500);
    Graphics2D g2d = (Graphics2D) g;
    g2d.setStroke(DASHED_STROKE);
    g2d.drawLine(500, 500, 100, 100);
    
    int radius = (int) (Math.sqrt(DogOnCurve.RADIUS_SQUARED) * 400);
    g2d.drawArc(100 - radius, 500 - radius, 2 * radius, 2 * radius, 0, 90);
    
    g.setFont(new Font("Serif", Font.BOLD, 36));
    g.setColor(Color.ORANGE);
    if (foodAtX) {
      g.drawString("HUNT", 500, 500);
    } else {
      g.drawString("BEG", 100, 100);
    }
    
    g.setColor(Color.RED);
    g.drawString(Integer.toString(dogOnLine.foodWon),
        (int) (100 + 400 * dogOnLine.currentPointX), 
        (int) (500 - 400 * dogOnLine.currentPointY));
    
    g.setColor(Color.GREEN);
    g.drawString(Integer.toString(dogOnCurve.foodWon),
        (int) (100 + 400 * dogOnCurve.currentPointX), 
        (int) (500 - 400 * dogOnCurve.currentPointY));
  }
  
  public void createAndShowGUI() {
    // Create and set up the window.
    JFrame frame = new JFrame("Term Vector Plotter");
    frame.setSize(new Dimension(600, 600));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setContentPane(this);
    // Display the window.
    frame.setVisible(true);
  }
  
  public static void main(String[] args) {
    PurposefulChoiceDemo demo = new PurposefulChoiceDemo();
    demo.createAndShowGUI();
    int turns = 0;
    while (turns < MAX_TURNS) {
      demo.dropFood();
      System.out.println(demo.currentScores());
      demo.removeAll();
      demo.updateUI();
      try {
        //Thread.sleep(1);
        Thread.sleep((int) 10000.0 / (turns + 5));
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      ++turns;
    }
  }
}
