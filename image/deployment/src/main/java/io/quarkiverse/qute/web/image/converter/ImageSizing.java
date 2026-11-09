package io.quarkiverse.qute.web.image.converter;

public final class ImageSizing {

    private ImageSizing() {
    }

    public static int computeTargetHeight(int targetWidth, int sourceWidth, int sourceHeight, String cropRatio) {
        if (cropRatio != null) {
            String[] parts = cropRatio.split(":");
            if (parts.length == 2) {
                double ratioW = Double.parseDouble(parts[0]);
                double ratioH = Double.parseDouble(parts[1]);
                return (int) Math.round(targetWidth * ratioH / ratioW);
            }
        }
        return (int) Math.round((double) sourceHeight * targetWidth / sourceWidth);
    }
}
