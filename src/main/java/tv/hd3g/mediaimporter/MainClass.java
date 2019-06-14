package tv.hd3g.mediaimporter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javafx.application.Application;

public class MainClass {

	public static final String DIGEST_NAME = System.getProperty("integrity.digest.name", "SHA1");

	public static void main(final String[] args) throws NoSuchAlgorithmException {
		MessageDigest.getInstance(DIGEST_NAME);
		Application.launch(MainApp.class);
	}

}
