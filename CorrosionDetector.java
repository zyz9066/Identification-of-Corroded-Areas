package ca.ubishops.yunxiuzhang.summerProjectChallenge1;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Calendar;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

public class CorrosionDetector {
	
	private static Scanner scan = new Scanner(System.in);

	private static final String DOT = ".";

	private static final String _RESULT = "_result";

	private String sourceFolder;

	private String imageExt;

	private String imageSetFolder;

	public String getSourceFolder() {
		return sourceFolder;
	}

	public void setSourceFolder(String sourceFolder) {
		this.sourceFolder = sourceFolder;
	}

	public String getImageExt() {
		return imageExt;
	}

	public void setImageExt(String imageExt) {
		this.imageExt = imageExt;
	}

	public String getImageSetFolder() {
		return imageSetFolder;
	}

	public void setImageSetFolder(String imageSetFolder) {
		this.imageSetFolder = imageSetFolder;
	}

	private int[][] train() throws Exception {
		int[][] histogram = new int[32][32];
		File folder = new File(imageSetFolder);
		for (final File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory()) {
				continue;
			}
			BufferedImage img1 = ImageIO.read(fileEntry);
			getHistogram(img1, histogram);
		}

		int max = Integer.MIN_VALUE;
		for (int i = 0; i < histogram.length; i++) {
			for (int j = 0; j < histogram[0].length; j++) {
				if (histogram[i][j] > max) {
					max = histogram[i][j];
				}
			}
		}
		System.out.println("max==" + max);
		for (int i = 0; i < histogram.length; i++) {
			for (int j = 0; j < histogram[0].length; j++) {
				if (histogram[i][j] < 0.1 * max) {
					histogram[i][j] = 0;
				}
			}
		}
		return histogram;
	}

	private void getHistogram(BufferedImage img, int[][] histogram) {

		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int rgb = img.getRGB(x, y);
				Color color = new Color(rgb, true);
//				int avg = ((color.getRed() + color.getGreen() + color.getBlue()) / 3);
//				float[] hsv = new float[3];// HSB is the same as HSV but HSL is different
				float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
				hsv[0] = hsv[0] * (histogram.length - 1);
				hsv[1] = hsv[1] * (histogram.length - 1);
				hsv[2] = hsv[2] * (histogram.length - 1);
//				System.out.println("hsv==("+hsv[0]+","+hsv[1]+","+hsv[2]+")");
				histogram[(int) hsv[0]][(int) hsv[1]]++;
			}
		}

		return;
	}

	private int[][] getDownSampledGrayScale(BufferedImage img) throws Exception {
		int[][] ret = new int[img.getHeight()][img.getWidth()];
		for (int x = 0; x < img.getWidth(); x++) {
			for (int y = 0; y < img.getHeight(); y++) {
				int rgb = img.getRGB(x, y);
				Color color = new Color(rgb, true);
				int avg = ((color.getRed() + color.getGreen() + color.getBlue()) / 3);
				avg = avg * 32 / 256;
				ret[y][x] = avg;
			}
		}
		return ret;
	}

	private double getEnergy(int[][] gray, ImagePatch p) throws Exception {
		double[][] glcm = getGLCM(gray, p);
		double ret = 0.0;
		for (int i = 0; i < 32; i++) {
			for (int j = 0; j < 32; j++) {
				ret = ret + Math.pow(glcm[i][j], 2);
			}
		}
		return ret;
	}

	private double[][] getGLCM(int[][] gray, ImagePatch p) throws Exception {
		double[][] glcm = new double[32][32];
		int d = 5;// a=0
		double sum = 0.0;
		for (int r = p.getY(); r < p.getY() + p.getHeight(); r++) {
			for (int c = p.getX(); c < p.getX() + p.getWidth() - d; c++) {
				int ii = gray[r][c];
				int jj = gray[r][c + d];
				glcm[ii][jj]++;
				glcm[jj][ii]++;
				sum = sum + 2;
			}
		}
		for (int i = 0; i < 32; i++) {
			for (int j = 0; j < 32; j++) {
				glcm[i][j] = glcm[i][j] / sum;
			}
		}
		return glcm;
	}

	private void colorStep(ImagePatch p, int[][] histogram, BufferedImage img) {
//		System.out.println("p==" + p);
		int max = Integer.MIN_VALUE;
		for (int i = 0; i < histogram.length; i++) {
			for (int j = 0; j < histogram[0].length; j++) {
				if (histogram[i][j] > max) {
					max = histogram[i][j];
				}
			}
		}
		
		double mV = 50 * 32 / 256.0, mS = 50 * 32 / 256.0, MV = 200 * 32 / 256.0;
//		double mV = 50, mS = 50, MV = 200;
		for (int y = p.getY(); y < p.getY() + p.getHeight(); y++) {
			for (int x = p.getX(); x < p.getX() + p.getWidth(); x++) {
				int rgb = img.getRGB(x, y);
				Color color = new Color(rgb, true);
				float[] hsv = new float[3];// HSB is the same as HSV but HSL is different
				Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsv);
				hsv[0] = hsv[0] * (histogram.length - 1);
				hsv[1] = hsv[1] * (histogram.length - 1);
				hsv[2] = hsv[2] * (histogram.length - 1);

				if (hsv[2] < mV || (hsv[2] > MV && hsv[1] < mS)) {
					// non-corroded
//					System.out.println("not corroded,black&white hsv[2]=="+hsv[2]+"hsv[1]=="+hsv[1]);
				} else {// corroded

					int hs = histogram[(int) hsv[0]][(int) hsv[1]];

					if (hs > 0) {
						if (hs > 0.75 * max) {
							img.setRGB(x, y, Color.RED.getRGB());
						} else if (hs > 0.5 * max && hs <= 0.75 * max) {
							img.setRGB(x, y, Color.ORANGE.getRGB());
						} else if (hs > 0.25 * max && hs <= 0.5 * max) {
							img.setRGB(x, y, Color.GREEN.getRGB());
						} else if (hs >= 0.1 * max && hs <= 0.25 * max) {
							img.setRGB(x, y, Color.BLUE.getRGB());
						}
					} else {
						// non-corroded
//						System.out.println("non-corroded hist p=="+p);
					}
				}
			}
		}
	}

	private void roughnessStep(BufferedImage img,int[][] histogram) throws Exception {
		int[][] gray = getDownSampledGrayScale(img);

		ImagePatch p = null;
		double tE = 0.05;

		for (int y = 0; y < gray.length; y = y + 15) {
			for (int x = 0; x < gray[0].length; x = x + 15) {
				p = new ImagePatch();
				p.setX(x);
				p.setY(y);
				if (y + 14 < gray.length) {
					p.setHeight(15);
				} else {
					p.setHeight(gray.length - y);
				}
				if (x + 14 < gray[0].length) {
					p.setWidth(15);
				} else {
					p.setWidth(gray[0].length - x);
				}

				double energy = getEnergy(gray, p);
//				System.out.println("p==" + p);
//				System.out.println("energy==" + energy);
				if (energy < tE) {
//					System.out.println("p=="+p);
					colorStep(p, histogram, img);
				}

			}
		}

	}

	public void detect() throws Exception {
		if(this.imageSetFolder==null) {
			throw new Exception("imageSetFolder is required");
		}
		if(this.sourceFolder==null) {
			throw new Exception("sourceFolder is required");
		}
		int[][] histogram = train();
		File folder = new File(sourceFolder);

		for (final File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory() || (imageExt != null && !fileEntry.getName().endsWith(imageExt))||fileEntry.getName().contains(_RESULT)) {
				continue;
			}
			BufferedImage img = ImageIO.read(fileEntry);
			
			roughnessStep(img,histogram);
			
			String fileName = fileEntry.getName();
			fileName = folder.getAbsolutePath()+File.separator+fileName.substring(0, fileName.indexOf(DOT)) + _RESULT
					+ fileName.substring(fileName.indexOf(DOT));
			ImageIO.write(img,fileName.substring(fileName.indexOf(DOT)+1),new File(fileName));
//			ImageIO.write(img,"JPG",new File(fileName));
		}


	}
	
	

	public static void main(String[] args) throws Exception {
		
		try {

			String greetings = getGreetings();

			System.out.println("****************************************************");
			System.out.println("************Corrosion Detection Application****************");
			System.out.println("******By Yunxiu Zhang, Xiaoping Yu, Tianye Zhao*********");
			System.out.println("****************************************************");
			System.out.println();
			System.out.println(greetings + "! Welcome to Corrosion Detection Application.");
			System.out.println();
			System.out.println("This application is the solution of CS590 Challenge 1 ");
			System.out.println("****************************************************");

			

			while (true) {
				System.out.println();
				System.out.println("Please choose below menu to continue...");
				System.out.println("********************* Menu ************************");
				System.out.println("*****************1. Inspect pictures*********************");
				System.out.println("*****************0. Exit*********************");
				System.out.println();

				System.out.println("Please input a number to choose a menu");
				System.out.println();

				String input = scan.nextLine();
				if (input == null || input.length() != 1 || input.charAt(0) < '0' || input.charAt(0) > '1') {
					System.err.println("Invalid input, please try again");
					continue;
				}

				int num = Integer.parseInt(input);

				switch (num) {
				case 1:
					inspectImages();
					break;
				case 0:
					scan.close();
					System.out.println("Goodbye...");
					System.exit(0);
				default:
					System.err.println("Invalid input, please try again");
					break;
				}

			}			

		} catch (Exception e) {
			e.printStackTrace();
		}		

	}
	
	private static void inspectImages() throws Exception {
		CorrosionDetector d = new CorrosionDetector();
//		d.setImageExt("png");
//		d.setImageSetFolder("C:/BS/SummerProject/set");
//		d.setSourceFolder("C:/BS/SummerProject/pics");
		
		d.setImageSetFolder(getImageSet());
		d.setSourceFolder(getPictureFolder());
		
		d.detect();
	}
	
	private static String getPictureFolder() {

		System.out.println("Please input the full location of the pictures for corrosion detection");
		System.out.println();
		String path = scan.nextLine();

		File file = new File(path);
		while (!file.exists() || !file.isDirectory()) {
			String msg = "";
			if (!file.exists()) {
				msg = "Sorry, the folder does not exist, please input again";
			} else if (!file.isDirectory()) {
				msg = "Sorry, this is not a directory, please input again";
			} 
			System.out.println();
			System.err.println(msg);
			System.out.println();
			path = scan.nextLine();

			file = new File(path);
		}
		return path;
	}
	
	private static String getImageSet() {

		System.out.println("Please input the full location of the image data set for training");
		System.out.println();
		String path = scan.nextLine();

		File file = new File(path);
		while (!file.exists() || !file.isDirectory()) {
			String msg = "";
			if (!file.exists()) {
				msg = "Sorry, the folder does not exist, please input again";
			} else if (!file.isDirectory()) {
				msg = "Sorry, this is not a directory, please input again";
			} 
			System.out.println();
			System.err.println(msg);
			System.out.println();
			path = scan.nextLine();

			file = new File(path);
		}
		return path;
	}
	
	
	private static String getGreetings() {
		String msg = null;
		Calendar c = Calendar.getInstance();
		int hour = c.get(Calendar.HOUR_OF_DAY);
		if (hour >= 0 && hour < 12) {
			msg = "Good Morning";
		} else if (hour >= 12 && hour < 16) {
			msg = "Good Afternoon";
		} else if (hour >= 16 && hour < 22) {
			msg = "Good Evening";
		} else {
			msg = "Good Night";
		}
		return msg;
	}

}
