package tv.hd3g.mediaimporter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javafx.application.Application;

public class MainClass {

	public static final String[] DIGEST_NAMES = System.getProperty("integrity.digest.names", "MD5,SHA,SHA-256").split(",");

	public static void main(final String[] args) throws NoSuchAlgorithmException {
		for (int i = 0; i < DIGEST_NAMES.length; i++) {
			MessageDigest.getInstance(DIGEST_NAMES[i]);
		}
		Application.launch(MainApp.class);
	}

}
