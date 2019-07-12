
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

public class CorrosionMultiThreadingDetector {

	private static final String _RESULT = "_result";

	private String sourceFolder;

	private String imageExt;

	private String imageSetFolder;

	private class ColorImage {
		private BufferedImage img;
		private List<ImagePatch> patchList;
		private File file;		

		public File getFile() {
			return file;
		}

		public void setFile(File file) {
			this.file = file;
		}

		public ColorImage() {
			this.patchList = new ArrayList<>();
		}

		public BufferedImage getImg() {
			return img;
		}

		public void setImg(BufferedImage img) {
			this.img = img;
		}

		public List<ImagePatch> getPatchList() {
			return patchList;
		}

		public void addPatch(ImagePatch p) {
			patchList.add(p);
		}
	}

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

	private void roughnessStep(ColorImage img) throws Exception {
		int[][] gray = getDownSampledGrayScale(img.getImg());

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
				if (energy < tE) {
//					System.out.println("p=="+p);
					img.addPatch(p);
//					colorStep(p, histogram, img);
				}

			}
		}
		return;
	}

	private class Trainer implements Callable<int[][]> {
		private final CountDownLatch signal;

		public Trainer(CountDownLatch signal) {
			this.signal = signal;
		}
		public int[][] call() throws Exception {

			int[][] hist = train();
			signal.countDown();
			return hist;
			// return;
		}
	}
	private class RoughnessStep implements Callable<Integer>{
		
		private BlockingQueue<ColorImage> inputImageQueue;
		
		private BlockingQueue<ColorImage> colorImageQueue;
		private int count;
		
		public RoughnessStep(BlockingQueue<ColorImage> inputImageQueue,BlockingQueue<ColorImage> colorImageQueue,int count) {
			this.inputImageQueue=inputImageQueue;
			this.colorImageQueue=colorImageQueue;
			this.count=count;
		}
		
		@Override
		public Integer call() throws Exception {
			ColorImage img = null;
			int num=count;
			while(num>0) {
				img = inputImageQueue.poll(10,TimeUnit.MINUTES);
				if(img==null)
					break;
				roughnessStep(img);
				colorImageQueue.offer(img);
				num--;
			}
			return 1;
		}
	}
	
private class ColorStep implements Callable<Integer>{
		
		private BlockingQueue<ColorImage> outputImageQueue;
		
		private BlockingQueue<ColorImage> colorImageQueue;
		
		private int[][] histogram;
		
		private final CountDownLatch signal;
		
		private int count;
		
		
		public ColorStep(BlockingQueue<ColorImage> outputImageQueue,BlockingQueue<ColorImage> colorImageQueue,int[][] histogram,CountDownLatch signal,int count) {
			this.outputImageQueue=outputImageQueue;
			this.colorImageQueue=colorImageQueue;
			this.histogram=histogram;
			this.signal=signal;
			this.count=count;
		}
		
		@Override
		public Integer call() throws Exception {
			signal.await();
			ColorImage img = null;
			int num=count;
			while(num>0) {
				img = colorImageQueue.poll(10,TimeUnit.MINUTES);
				if(img==null)
					break;
				List<ImagePatch> plst = img.getPatchList();
				for(ImagePatch p:plst) {
					colorStep(p,histogram,img.getImg());
				}
				outputImageQueue.offer(img);
				num--;
			}
			return 1;
		}
	}

private class ImageWriter implements Callable<Integer>{
	
	private BlockingQueue<ColorImage> outputImageQueue;
	
	private int count;
	
	
	public ImageWriter(BlockingQueue<ColorImage> outputImageQueue,int count) {
		this.outputImageQueue=outputImageQueue;
		this.count=count;
	}
	
	@Override
	public Integer call() throws Exception {
		ColorImage img = null;
		int num=count;
		while(num>0) {
			img = outputImageQueue.poll(10,TimeUnit.MINUTES);
			
			if(img==null)
				break;
		
			String fileName = img.getFile().getName();
			String path = img.getFile().getAbsolutePath();
			path=path.substring(0,path.lastIndexOf(File.separator));
			fileName = path+File.separator+fileName.substring(0, fileName.indexOf(".")) + _RESULT
					+ fileName.substring(fileName.indexOf("."));
			ImageIO.write(img.getImg(),fileName.substring(fileName.indexOf(".")+1),new File(fileName));
			num--;
		}
		return 1;
	}
}

private class ImageReader implements Callable<Integer>{
	
	private BlockingQueue<ColorImage> inputImageQueue;
	
	
	
	
	public ImageReader(BlockingQueue<ColorImage> inputImageQueue) {
		this.inputImageQueue=inputImageQueue;
	}
	
	@Override
	public Integer call() throws Exception {
		File folder = new File(sourceFolder);
		for (final File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory() || (imageExt != null && !fileEntry.getName().endsWith(imageExt))
					|| fileEntry.getName().contains(_RESULT)) {
				continue;
			}
			
			BufferedImage img = ImageIO.read(fileEntry);
			ColorImage ci = new ColorImage();
			ci.setImg(img);
			ci.setFile(fileEntry);
			inputImageQueue.offer(ci, 20, TimeUnit.MINUTES);
		}
		return 1;
	}
}

	public void detect() throws Exception {
		if (this.imageSetFolder == null) {
			throw new Exception("imageSetFolder is required");
		}
		if (this.sourceFolder == null) {
			throw new Exception("sourceFolder is required");
		}
		File folder = new File(sourceFolder);
		String[] list = folder.list(new FilenameFilter() {
		    @Override
		    public boolean accept(File dir, String name) {
		        return !name.contains(_RESULT);
		    }
		});
		int count = list.length;
		
		int[][] histogram = train();
		BlockingQueue<ColorImage> outputImageQueue = new ArrayBlockingQueue<>(20);
		BlockingQueue<ColorImage> colorImageQueue = new ArrayBlockingQueue<>(20);
		BlockingQueue<ColorImage> inputImageQueue = new ArrayBlockingQueue<>(20);
		CountDownLatch signal = new CountDownLatch(1);
		
		ImageReader reader = new ImageReader(inputImageQueue);
		Trainer trainer = new Trainer(signal);
		RoughnessStep rough = new RoughnessStep(inputImageQueue,colorImageQueue,count);
		ColorStep color = new ColorStep(outputImageQueue, colorImageQueue,histogram,signal,count);
		ImageWriter writer = new ImageWriter(outputImageQueue,count);
		
		ExecutorService readerPool = Executors.newSingleThreadExecutor();
		ExecutorService writerPool = Executors.newCachedThreadPool();
		ExecutorService trainerPool = Executors.newSingleThreadExecutor();
		ExecutorService roughPool = Executors.newSingleThreadExecutor();
		ExecutorService colorPool = Executors.newSingleThreadExecutor();		

		
		Future f1 = readerPool.submit(reader);		
		Future f2 = trainerPool.submit(trainer);
		Future f3 = roughPool.submit(rough);
		Future f4 = colorPool.submit(color);
		Future f5 = writerPool.submit(writer);
		
		readerPool.shutdown();		
		trainerPool.shutdown();
		roughPool.shutdown();
		colorPool.shutdown();
		writerPool.shutdown();
		
		f1.get();
		f2.get();
		f3.get();
		f4.get();
		f5.get();

	}

	public static void main(String[] args) throws Exception {
		long begin= System.nanoTime();
		CorrosionMultiThreadingDetector d = new CorrosionMultiThreadingDetector();
		d.setImageExt("png");
		d.setImageSetFolder("C:/BS/SummerProject/set");
		d.setSourceFolder("C:/BS/SummerProject/pics");

		d.detect();
		
		long time=System.nanoTime()-begin;
		System.out.println("time=="+TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));

	}

}
