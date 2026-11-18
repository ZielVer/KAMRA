package com.mycompany.kamra;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 *
 * @author Kier Tan
 */
public class KamraUI extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(KamraUI.class.getName());

    private volatile boolean isRecording = false;
    private volatile boolean requestSnapshot = false;
    private volatile boolean toggleRecordingRequested = false;

    private static final File MEDIA_DIR = new File("kamra_media");
    private OpenCVFrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private Thread cameraThread;
    private JPanel panelGallery;
    
    private volatile String activeFilter = "Camcorder 90s";
    private JComboBox<String> cmbFilters;
    private CascadeClassifier faceDetector;
    private BufferedImage imgShades;

    public KamraUI() {
        initComponents();
        
        String[] filters = {"Camcorder 90s", "Shades", "No Filter"};
        cmbFilters = new JComboBox<>(filters);
        cmbFilters.addActionListener(e -> activeFilter = (String) cmbFilters.getSelectedItem());
        jPanel1.add(cmbFilters);

        loadArAssets();

        if (!MEDIA_DIR.exists()) {
            MEDIA_DIR.mkdirs();
        }

        panelGallery = new JPanel();
        panelGallery.setLayout(new BoxLayout(panelGallery, BoxLayout.Y_AXIS));
        panelGallery.setBackground(new Color(24, 24, 24));
        jScrollPane1.setViewportView(panelGallery);
        jScrollPane1.setBorder(null);
        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.getVerticalScrollBar().setUnitIncrement(16);

        refreshGallery();

        startCameraStream();
    }
    
    private void startCameraStream() {
        cameraThread = new Thread(() -> {
            try {
                grabber = new OpenCVFrameGrabber(0);
                grabber.start();

                int actualWidth = grabber.getImageWidth();
                int actualHeight = grabber.getImageHeight();
                if (actualWidth <= 0 || actualHeight <= 0) {
                    actualWidth = 1280;
                    actualHeight = 720;
                }

                Java2DFrameConverter converter = new Java2DFrameConverter();
                OpenCVFrameConverter.ToMat toMatConverter = new OpenCVFrameConverter.ToMat();
                RectVector detectedFaces = new RectVector();
                Mat grayMat = new Mat();
                Mat smallGray = new Mat();

                Random rand = new Random();

                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM. dd yyyy", Locale.US);
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss a", Locale.US);
                SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

                long recordStartTime = 0;
                File currentVideoFile = null;

                while (!Thread.currentThread().isInterrupted() && isDisplayable()) {
                    try {
                        Frame rawFrame = grabber.grab();
                        if (rawFrame == null) continue;

                        BufferedImage image = converter.convert(rawFrame);
                        if (image == null) continue;

                        if (toggleRecordingRequested) {
                            toggleRecordingRequested = false;
                            if (!isRecording) {
                                String fileName = "KAMRA_VID_" + fileDateFormat.format(new Date()) + ".mp4";
                                currentVideoFile = new File(MEDIA_DIR, fileName);
                                recorder = new FFmpegFrameRecorder(currentVideoFile, actualWidth, actualHeight);
                                recorder.setFrameRate(30);
                                recorder.setVideoCodecName("libx264");
                                recorder.setFormat("mp4");
                                recorder.start();

                                recordStartTime = System.currentTimeMillis();
                                isRecording = true;
                                SwingUtilities.invokeLater(() -> {
                                    btnRecord.setText("Stop Recording");
                                    btnRecord.setBackground(new Color(200, 30, 30));
                                });
                            } else {
                                isRecording = false;
                                if (recorder != null) {
                                    recorder.stop();
                                    recorder.release();
                                    recorder = null;
                                }
                                SwingUtilities.invokeLater(() -> {
                                    btnRecord.setText("Start Recording");
                                    btnRecord.setBackground(new Color(45, 45, 45));
                                });

                                final File recordedFile = currentVideoFile;
                                SwingUtilities.invokeLater(() -> addMediaItemToGallery(recordedFile));
                            }
                        }

                        String currentFilter = activeFilter;

                        if ("Camcorder 90s".equals(currentFilter)) {
                            applyRetroFilter(image, rand);
                            
                            Graphics2D g2d = image.createGraphics();
                            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                            Font osdFont = new Font("Courier New", Font.BOLD, (int) (actualWidth * 0.035f));
                            g2d.setFont(osdFont);

                            int margin = (int) (actualWidth * 0.05f);
                            int bottom = actualHeight - margin;

                            Date now = new Date();
                            drawShadowText(g2d, dateFormat.format(now).toUpperCase(Locale.US), margin, bottom - (int) (osdFont.getSize() * 1.2), Color.WHITE);
                            drawShadowText(g2d, timeFormat.format(now).toUpperCase(Locale.US), margin, bottom, Color.WHITE);

                            if (isRecording) {
                                long elapsed = System.currentTimeMillis() - recordStartTime;
                                long totalSecs = elapsed / 1000;
                                String durationText = String.format("%02d:%02d:%02d", totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60);
                                int textWidth = g2d.getFontMetrics().stringWidth(durationText);
                                drawShadowText(g2d, durationText, actualWidth - margin - textWidth, bottom, new Color(220, 20, 20));

                                if ((elapsed / 500) % 2 == 0) {
                                    String rec = "REC ●";
                                    drawShadowText(g2d, rec, actualWidth - margin - g2d.getFontMetrics().stringWidth(rec), margin + osdFont.getSize(), new Color(220, 20, 20));
                                }
                            } else {
                                String stby = "STBY";
                                drawShadowText(g2d, stby, actualWidth - margin - g2d.getFontMetrics().stringWidth(stby), margin + osdFont.getSize(), new Color(180, 180, 180));
                            }
                            g2d.dispose();
                        } else if ("Shades".equals(currentFilter)) {
                            applyFaceArFilter(rawFrame, image, toMatConverter, detectedFaces, grayMat, smallGray);
                        }

                        if (requestSnapshot) {
                            requestSnapshot = false;
                            String photoName = "KAMRA_IMG_" + fileDateFormat.format(new Date()) + ".png";
                            File photoFile = new File(MEDIA_DIR, photoName);
                            ImageIO.write(image, "PNG", photoFile);
                            SwingUtilities.invokeLater(() -> addMediaItemToGallery(photoFile));
                        }

                        if (isRecording && recorder != null) {
                            Frame processedFrame = converter.convert(image);
                            recorder.record(processedFrame);
                        }

                        int labelW = lblCamera.getWidth();
                        int labelH = lblCamera.getHeight();
                        if (labelW > 0 && labelH > 0) {
                            double scale = Math.min((double) labelW / actualWidth, (double) labelH / actualHeight);
                            int targetW = Math.max(1, (int) (actualWidth * scale));
                            int targetH = Math.max(1, (int) (actualHeight * scale));

                            Image scaledImg = image.getScaledInstance(targetW, targetH, Image.SCALE_FAST);
                            ImageIcon icon = new ImageIcon(scaledImg);

                            SwingUtilities.invokeLater(() -> lblCamera.setIcon(icon));
                        }
                    } catch (IOException frameEx) {
                    }
                }
            } catch (FrameGrabber.Exception e) {
            } finally {
                cleanup();
            }
        });
        cameraThread.setDaemon(true);
        cameraThread.start();
    }
    
    private void refreshGallery() {
        panelGallery.removeAll();
        File[] files = MEDIA_DIR.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".mp4"));
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                addMediaItemToGallery(f);
            }
        }
        panelGallery.revalidate();
        panelGallery.repaint();
    }
    
    private void addMediaItemToGallery(File file) {
        boolean isVideo = file.getName().endsWith(".mp4");
        JPanel item = new JPanel(new java.awt.BorderLayout());
        item.setMaximumSize(new Dimension(190, 130));
        item.setPreferredSize(new Dimension(190, 130));
        item.setBackground(new Color(35, 35, 35));
        item.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));
        item.setCursor(new Cursor(Cursor.HAND_CURSOR));

        BufferedImage thumb = null;
        if (isVideo) {
            try (FFmpegFrameGrabber fg = new FFmpegFrameGrabber(file)) {
                fg.start();
                Frame f = fg.grabImage();
                if (f != null) thumb = new Java2DFrameConverter().convert(f);
                fg.stop();
            } catch (Exception ignored) {}
        } else {
            try { 
                thumb = ImageIO.read(file); 
            } catch (IOException ignored) {}
        }

        JLabel thumbLbl = new JLabel();
        thumbLbl.setHorizontalAlignment(SwingConstants.CENTER);
        if (thumb != null) {
            thumbLbl.setIcon(new ImageIcon(thumb.getScaledInstance(170, 90, Image.SCALE_SMOOTH)));
        } else {
            thumbLbl.setText(isVideo ? "[VIDEO]" : "[PHOTO]");
            thumbLbl.setForeground(Color.GRAY);
        }
        item.add(thumbLbl, java.awt.BorderLayout.CENTER);

        JLabel nameLbl = new JLabel((isVideo ? "🎬 " : "📷 ") + file.getName(), SwingConstants.CENTER);
        nameLbl.setFont(new Font("Monospaced", Font.PLAIN, 10));
        nameLbl.setForeground(Color.LIGHT_GRAY);
        item.add(nameLbl, java.awt.BorderLayout.SOUTH);

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file);
                } catch (IOException ex) {
                }
            }
        });

        panelGallery.add(item, 0);
        panelGallery.revalidate();
        panelGallery.repaint();
    }
    
    private static void applyRetroFilter(BufferedImage image, Random rand) {
        if (image == null || image.getRaster() == null) return;
        if (image.getRaster().getDataBuffer() instanceof DataBufferByte dataBufferByte) {
            byte[] pixels = dataBufferByte.getData();
            int channels = image.getColorModel().getNumComponents();
            if (channels >= 3) {
                for (int i = 0; i < pixels.length; i += channels) {
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
    
    private void applyFaceArFilter(Frame rawFrame, BufferedImage image, OpenCVFrameConverter.ToMat toMatConverter, RectVector faces, Mat grayMat, Mat smallGray) {
        if (faceDetector == null || faceDetector.empty()) return;

        Mat colorMat = toMatConverter.convert(rawFrame);
        if (colorMat == null || colorMat.empty()) return;

        // Downscale by 50% in grayscale for fast face detection
        cvtColor(colorMat, grayMat, COLOR_BGR2GRAY);
        org.bytedeco.opencv.global.opencv_imgproc.resize(
    grayMat, 
    smallGray, 
    new org.bytedeco.opencv.opencv_core.Size(grayMat.cols() / 2, grayMat.rows() / 2), 
    0, 0, INTER_LINEAR
);
        equalizeHist(smallGray, smallGray);

        faceDetector.detectMultiScale(smallGray, faces, 1.1, 4, 0,
                new org.bytedeco.opencv.opencv_core.Size(40, 40),
                new org.bytedeco.opencv.opencv_core.Size());

        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (long i = 0; i < faces.size(); i++) {
            Rect r = faces.get(i);
            int fx = r.x() * 2;
            int fy = r.y() * 2;
            int fw = r.width() * 2;
            int fh = r.height() * 2;

            int shadesW = (int) (fw * 0.90);
            int shadesH = (int) (fh * 0.30);
            int shadesX = fx + (fw - shadesW) / 2;
            int shadesY = fy + (int) (fh * 0.24);

            if (imgShades != null) {
                g2d.drawImage(imgShades, shadesX, shadesY, shadesW, shadesH, null);
            } else {
                g2d.setColor(Color.BLACK);
                g2d.fillRect(shadesX, shadesY, shadesW, shadesH / 4);
                g2d.fillRect(shadesX + (int) (shadesW * 0.08), shadesY, (int) (shadesW * 0.36), shadesH);
                g2d.fillRect(shadesX + (int) (shadesW * 0.56), shadesY, (int) (shadesW * 0.36), shadesH);
            }
        }

        g2d.dispose();
    }
    
    private void loadArAssets() {
        try {
            File xmlFile = new File("assets/haarcascade_frontalface_default.xml");
            if (xmlFile.exists()) {
                faceDetector = new CascadeClassifier(xmlFile.getAbsolutePath());
            } else {
                System.err.println("Warning: haarcascade_frontalface_default.xml not found in assets/");
            }

            File shadesFile = new File("assets/sunglasses.png");
            if (shadesFile.exists()) imgShades = ImageIO.read(shadesFile);
        } catch (IOException e) {
        }
    }
    
    private static void drawShadowText(Graphics2D g2d, String text, int x, int y, Color color) {
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, x + 2, y + 2);
        g2d.setColor(color);
        g2d.drawString(text, x, y);
    }

    private void cleanup() {
        try {
            if (recorder != null) { recorder.stop(); recorder.release(); }
            if (grabber != null) { grabber.stop(); grabber.release(); }
        } catch (FFmpegFrameRecorder.Exception | FrameGrabber.Exception ignored) {}
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblCamera = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        btnPhoto = new javax.swing.JButton();
        btnRecord = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        lblCamera.setBackground(new java.awt.Color(20, 20, 20));
        lblCamera.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCamera.setOpaque(true);
        lblCamera.setPreferredSize(new java.awt.Dimension(960, 540));
        getContentPane().add(lblCamera, java.awt.BorderLayout.CENTER);

        jPanel1.setBackground(new java.awt.Color(15, 15, 15));

        btnPhoto.setText("Take Photo");
        btnPhoto.addActionListener(this::btnPhotoActionPerformed);
        jPanel1.add(btnPhoto);

        btnRecord.setText("Start Recording");
        btnRecord.addActionListener(this::btnRecordActionPerformed);
        jPanel1.add(btnRecord);

        getContentPane().add(jPanel1, java.awt.BorderLayout.PAGE_END);

        jPanel2.setPreferredSize(new java.awt.Dimension(220, 0));
        jPanel2.setLayout(new java.awt.BorderLayout());

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("GALLERY");
        jLabel1.setAlignmentY(0.0F);
        jPanel2.add(jLabel1, java.awt.BorderLayout.NORTH);
        jPanel2.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        getContentPane().add(jPanel2, java.awt.BorderLayout.LINE_END);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnPhotoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPhotoActionPerformed
        // TODO add your handling code here:
        requestSnapshot = true;
    }//GEN-LAST:event_btnPhotoActionPerformed

    private void btnRecordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecordActionPerformed
        // TODO add your handling code here:
        toggleRecordingRequested = true;
    }//GEN-LAST:event_btnRecordActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new KamraUI().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnPhoto;
    private javax.swing.JButton btnRecord;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblCamera;
    // End of variables declaration//GEN-END:variables
}
