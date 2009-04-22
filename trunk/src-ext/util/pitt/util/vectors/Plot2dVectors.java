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
	static final int pad = 50;
	static final int comp1 = 1;
	static final int comp2 = 2;
	static final int maxplot = 50;
	// Setting this to true will make the plotter output TeX source to
	// your consolve.
	public final boolean PRINT_TEX_OUTPUT = true;

	public Plot2dVectors (ObjectVector[] vectors) {
		System.err.println("Constructing plotter ...");
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

		System.err.println("Painting component ...");
		if (PRINT_TEX_OUTPUT) {
			int len = scale + pad;
			System.out.println("\\begin{figure}[t]");
			System.out.println("\\begin{center}");
			System.out.println("\\footnotesize");
			System.out.println("\\setlength{\\unitlength}{.55pt}");
			System.out.println();
			System.out.println("\\begin{picture}(" + len + "," + len + ")");
			System.out.println("\\put(0,0){\\framebox(" + len + "," + len + "){}}");
		}

		for (int i = 0; i < vectors.length; ++i) {
	    c1 = (pad/2) + Math.round(scale*(vectors[i].getVector()[comp1]-min1)
																/ (max1-min1));
	    c2 = (pad/2) + Math.round(scale*(vectors[i].getVector()[comp2]-min2)
																/ (max2-min2));
	    
	    g.drawString(vectors[i].getObject().toString(), c1, c2);        
	    if( i > maxplot ){
				break;
	    }

			if (PRINT_TEX_OUTPUT) {
				System.out.println("\\put(" + c1 + "," + c2 + ")" + "{\\makebox(0,0){"
													 + vectors[i].getObject().toString() + "}}");

			}
		}

		if (PRINT_TEX_OUTPUT) {
			System.out.println("\\end{picture}");
			System.out.println("\\caption{ADD CAPTION}");
			System.out.println("\\label{ADD LABEL}");
			System.out.println("\\end{center}");
			System.out.println("\\end{figure}");
		}

		System.err.println("Finished painting component ...");
	}


	public void createAndShowGUI() {
		// Create and set up the window.
		JFrame frame = new JFrame("Term Vector Plotter");
		frame.setSize(new Dimension(scale+2*pad, scale+2*pad));
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		System.err.println("Trying to set content pane ...");
		frame.setContentPane(this);
		// Display the window.
		frame.setVisible(true);
	}
}
