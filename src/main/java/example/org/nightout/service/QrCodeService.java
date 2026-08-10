package example.org.nightout.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.Club;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class QrCodeService {

    private final AppProperties properties;

    public QrCodeService(AppProperties properties) {
        this.properties = properties;
    }

    public byte[] clubQrPng(Club club) {
        try {
            String targetUrl = properties.getBaseUrl().replaceAll("/$", "") + "/clubs/" + club.getSlug();
            BitMatrix matrix = new QRCodeWriter().encode(targetUrl, BarcodeFormat.QR_CODE, 512, 512);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Could not generate QR code.", ex);
        }
    }
}
