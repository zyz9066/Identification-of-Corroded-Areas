# Identification-of-Corroded-Areas

## Introduction

The aim is to use a data driven approach to evaluate corrosion spread across a large dataset of high quality dense image data.

## Solution

The solution we use is based on the Corrosion Detection for Automated Visual Inspection, Weak-classifier Colour-based Corrosion Detector (WCCD).  The solution consists of 3 major steps: training step, roughness step and color step.

### Training step

This is the preparation step for the third step. We cut some of the known corroded area from some sample images and save them in a folder to serve as training image set. Then we load them to construct a HS based 2-dimensional histogram.  After that, we get the max value from histogram and update all values less than 10%\*max to 0. 

### Roughness step

The basic idea is that a corroded area presents a rough texture and the roughness can be evaluated with the energy of the symmetric gray-level co-occurrence-matrix (GLCM). 

To calculate the energy for all 15\*15 patches of each image, we read the image and get its gray scale presentation array, and then calculate the GLCM and energy for each 15\*15 patch. If an patch has energy less than a threshold (0.05), which means it’s rough, we pass it to next color step for further analysis. 

### Color Step

In this step, we read the image and get their HSV presentation array. 

Then, we ignore (label as non-corroded) the pixels close to black or white, which are very unlikely to be in corroded area.
After that, we check the HS of the pixel to see if its histogram value is 0. If not 0, we label it as corroded with different colors based on its histogram value:

* red if HS(h, s) 2 [0.75HS, 1.00HS],
* orange if HS(h, s) 2 [0.50HS, 0.75HS],
* green if HS(h, s) 2 [0.25HS, 0.50HS] and
* blue if HS(h, s) 2 [0.10HS, 0.25HS],

## Tech implementation

Our solution would be implemented in Java (JDK8) without any external framework, library or tool. 

## Improvement 

To improve the results, the paper suggests three methods: Downsample to 32 pixels, Parzen windows, and Bilateral filter. We use the Downsample-to-32 pixels one.  

We also tried to make the program multi-threading to improve its performance. 
