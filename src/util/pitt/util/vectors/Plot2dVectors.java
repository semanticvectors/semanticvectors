// Add Google copyright.

package pitt.util.vectors;

import java.lang.Float;
import java.lang.Double;
import java.lang.Math;

import java.awt.*;    
import javax.swing.*; 

import pitt.search.semanticvectors.*;

public class Plot2dVectors extends JPanel {
	ObjectVector[] vectors;
	static final int scale = 500;
	static final int pad = 40;
	static final int comp1 = 1;
	static final int comp2 = 2;
	static final int maxplot = 50;

	public Plot2dVectors (ObjectVector[] vectors) {
		System.out.println("Constructing plotter ...");
		this.vectors = vectors;
		this.setSize(new Dimension(scale + 2*pad, scale + 2*pad));
	}

	public void paintComponent(Graphics g) {
		int c1, c2;
		float min1, max1, min2, max2;
		min1 = min2 = 100;
		max1 = max2 = -100;
		// Compute max and min.
		for (int i = 0; i < vectors.length; ++i) {
	    if (vectors[i].getVector()[comp1] < min1) {
				min1 = vectors[i].getVector()[comp1];
	    }
	    if (vectors[i].getVector()[comp2] < min2) {
				min2 = vectors[i].getVector()[comp2];
	    }
	    if (vectors[i].getVector()[comp1] > max1) {
				max1 = vectors[i].getVector()[comp1];
	    }
	    if (vectors[i].getVector()[comp2] > max2) {
				max2 = vectors[i].getVector()[comp2];
	    }
	    if (i > maxplot) {
				break;
	    }
		}

		System.out.println("Painting component ...");
		for (int i = 0; i < vectors.length; ++i) {
	    c1 = (pad/2)+Math.round(scale*(vectors[i].getVector()[comp1]-min1)/(max1-min1));
	    c2 = (pad/2)+Math.round(scale*(vectors[i].getVector()[comp2]-min2)/(max2-min2));
	    /*
				System.out.print(vectors[i].term + " ");
				System.out.print(c1);
				System.out.print(" ");
				System.out.println(c2);
	    */
	    g.drawString(vectors[i].getObject().toString(), c1, c2);        
	    if( i > maxplot ){
				break;
	    }
		}
		System.out.println("Finished painting component ...");
	}


	public void createAndShowGUI() {
		// Create and set up the window.
		JFrame frame = new JFrame("Term Vector Plotter");
		frame.setSize(new Dimension(scale+2*pad, scale+2*pad));
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		System.out.println("Trying to set content pane ...");
		frame.setContentPane(this);
		// Display the window.
		frame.setVisible(true);
	}
}
