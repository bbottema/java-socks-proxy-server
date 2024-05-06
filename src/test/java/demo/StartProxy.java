package demo;

import lombok.extern.slf4j.Slf4j;
import org.bbottema.javasocksproxyserver.SocksServer;
import org.bbottema.javasocksproxyserver.auth.UsernamePasswordAuthenticator;

import static java.lang.Integer.parseInt;

@Slf4j
public class StartProxy {

	private static final int DEFAULT_PORT = 8888;

	public static void main(String[] args) {
		new SocksServer(determinePort(args))
			.setAuthenticator(new UsernamePasswordAuthenticator(false) {
				@Override
				public boolean validate(String username, String password) {
					return username.equals("mysecureusername") && password.equals("mysecurepassword");
				}
			}).start();
	}

	private static int determinePort(String[] args) {
		if (args.length == 1) {
			try {
				return parseInt(args[0].trim());
			} catch (Exception e) {
				log.warn("Unable to parse port from command-line parameter, defaulting to: " + DEFAULT_PORT);
			}
		} else {
			log.info("Port not passed as command-line parameter, defaulting to: " + DEFAULT_PORT);
		}
		return DEFAULT_PORT;
	}
}