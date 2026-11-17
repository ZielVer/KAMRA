package com.mycompany.kamra;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

/**
 *
 * @author Kier Tan
 */
public class LaptopCamcorderApp {

    // Control flags modified via KeyListener
    private static volatile boolean isRecording = false;
    private static volatile boolean requestSnapshot = false;
    private static volatile boolean toggleRecordingRequested = false;

    public static void main(String[] args) {
        int frameRate = 30;

        OpenCVFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;
        CanvasFrame canvas = null;

        try {
            // 1. Initialize and start webcam
            grabber = new OpenCVFrameGrabber(0);
            grabber.start();

            int actualWidth = grabber.getImageWidth();
            int actualHeight = grabber.getImageHeight();
            if (actualWidth <= 0 || actualHeight <= 0) {
                actualWidth = 1280;
                actualHeight = 720;
            }

            // 2. Initialize Canvas window
            canvas = new CanvasFrame("K.A.M.R.A. - [SPACE: Photo | R: Record]", CanvasFrame.getDefaultGamma() / grabber.getGamma());
            canvas.setCanvasSize(actualWidth, actualHeight);
            canvas.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // 3. Make canvas focusable and bind keyboard + mouse listeners
            java.awt.Canvas awtCanvas = canvas.getCanvas();
            awtCanvas.setFocusable(true);
            canvas.setFocusable(true);

            KeyAdapter keyHandler = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                        requestSnapshot = true;
                    } else if (e.getKeyCode() == KeyEvent.VK_R) {
                        toggleRecordingRequested = true;
                    }
                }
            };

            // Attach to both the inner canvas and outer frame window
            awtCanvas.addKeyListener(keyHandler);
            canvas.addKeyListener(keyHandler);

            // Regain focus if the user clicks inside the video window
            awtCanvas.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    awtCanvas.requestFocusInWindow();
                }
            });

            // Request focus once the window is shown
            awtCanvas.requestFocusInWindow();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            Random rand = new Random();

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM. dd yyyy", Locale.US);
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss a", Locale.US);
            SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

            long recordStartTime = 0;

            while (canvas.isVisible()) {
                Frame rawFrame = grabber.grab();
                if (rawFrame == null) break;

                BufferedImage image = converter.convert(rawFrame);
                if (image == null) continue;

                // Handle toggle recording request
                if (toggleRecordingRequested) {
                    toggleRecordingRequested = false;
                    if (!isRecording) {
                        // START RECORDING
                        String fileName = "KAMRA_VID_" + fileDateFormat.format(new Date()) + ".mp4";
                        recorder = new FFmpegFrameRecorder(new File(fileName), actualWidth, actualHeight);
                        recorder.setFrameRate(frameRate);
                        recorder.setVideoCodecName("libx264");
                        recorder.setFormat("mp4");
                        recorder.start();
                        recordStartTime = System.currentTimeMillis();
                        isRecording = true;
                        System.out.println("Recording started: " + fileName);
                    } else {
                        // STOP RECORDING
                        isRecording = false;
                        if (recorder != null) {
                            recorder.stop();
                            recorder.release();
                            recorder = null;
                        }
                        System.out.println("Recording saved.");
                    }
                }

                // --- Apply Filter ---
                applyRetroFilter(image, rand);

                // --- Draw Camcorder HUD/OSD ---
                Graphics2D g2d = image.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Font osdFont = new Font("Courier New", Font.BOLD, (int) (actualWidth * 0.035f));
                g2d.setFont(osdFont);

                int margin = (int) (actualWidth * 0.05f);
                int bottom = actualHeight - margin;

                // Bottom Left: Date & Time
                Date now = new Date();
                String dateText = dateFormat.format(now).toUpperCase(Locale.US);
                String timeText = timeFormat.format(now).toUpperCase(Locale.US);
                drawShadowText(g2d, dateText, margin, bottom - (int) (osdFont.getSize() * 1.2), Color.WHITE);
                drawShadowText(g2d, timeText, margin, bottom, Color.WHITE);

                // Recording State Indicators
                if (isRecording) {
                    long elapsedMillis = System.currentTimeMillis() - recordStartTime;
                    long totalSecs = elapsedMillis / 1000;
                    String durationText = String.format("%02d:%02d:%02d", totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60);

                    // Bottom Right: Red Timer
                    int textWidth = g2d.getFontMetrics().stringWidth(durationText);
                    drawShadowText(g2d, durationText, actualWidth - margin - textWidth, bottom, new Color(220, 20, 20));

                    // Top Right: Blinking "REC ●"
                    if ((elapsedMillis / 500) % 2 == 0) {
                        String recText = "REC ●";
                        int recWidth = g2d.getFontMetrics().stringWidth(recText);
                        drawShadowText(g2d, recText, actualWidth - margin - recWidth, margin + osdFont.getSize(), new Color(220, 20, 20));
                    }
                } else {
                    // Top Right: Standby Mode
                    String standbyText = "STBY";
                    int sbWidth = g2d.getFontMetrics().stringWidth(standbyText);
                    drawShadowText(g2d, standbyText, actualWidth - margin - sbWidth, margin + osdFont.getSize(), new Color(180, 180, 180));
                }

                g2d.dispose();

                // Convert processed image back to frame
                Frame processedFrame = converter.convert(image);

                // Display on window
                canvas.showImage(processedFrame);

                // Write frame to video if currently recording
                if (isRecording && recorder != null) {
                    recorder.record(processedFrame);
                }

                // Save Photo Snapshot if Spacebar was pressed
                if (requestSnapshot) {
                    requestSnapshot = false;
                    String photoName = "KAMRA_IMG_" + fileDateFormat.format(new Date()) + ".png";
                    ImageIO.write(image, "PNG", new File(photoName));
                    System.out.println("Snapshot saved: " + photoName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (recorder != null) {
                    recorder.stop();
                    recorder.release();
                }
                if (grabber != null) {
                    grabber.stop();
                    grabber.release();
                }
                if (canvas != null) {
                    canvas.dispose();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void applyRetroFilter(BufferedImage image, Random rand) {
        if (image == null || image.getRaster() == null) return;

        if (image.getRaster().getDataBuffer() instanceof DataBufferByte) {
            byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            int numChannels = image.getColorModel().getNumComponents();

            if (numChannels >= 3) {
                for (int i = 0; i < pixels.length; i += numChannels) {
                    int b = pixels[i] & 0xFF;
                    int g = pixels[i + 1] & 0xFF;
                    int r = pixels[i + 2] & 0xFF;

                    int grain = rand.nextInt(25) - 12;

                    pixels[i]     = (byte) Math.min(255, Math.max(0, (int) (b * 0.90) + grain));
                    pixels[i + 1] = (byte) Math.min(255, Math.max(0, (int) (g * 1.05) + grain));
                    pixels[i + 2] = (byte) Math.min(255, Math.max(0, (int) (r * 0.95) + grain));
                }
            }
        }
    }

    private static void drawShadowText(Graphics2D g2d, String text, int x, int y, Color color) {
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, x + 2, y + 2);
        g2d.setColor(color);
        g2d.drawString(text, x, y);
    }
}