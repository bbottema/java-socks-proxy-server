package org.bbottema.javasocksproxyserver.auth;

import org.bbottema.javasocksproxyserver.AuthConstants;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class UsernamePasswordAuthenticator extends Authenticator {

    private final boolean acceptNoAuthMode;

    public UsernamePasswordAuthenticator(boolean acceptNoAuthMode) {
        this.acceptNoAuthMode = acceptNoAuthMode;
    }

    @Override
    public byte accept(byte[] authTypes) {
        if (acceptNoAuthMode) {
            for (byte type : authTypes) {
                if (type == AuthConstants.TYPE_NO_AUTH) {
                    return type;
                }
            }
        }
        for (byte type : authTypes) {
            if (type == AuthConstants.TYPE_USER_PASS_AUTH) {
                return type;
            }
        }
        return AuthConstants.NONE_ACCEPTED;
    }

    @Override
    public boolean validate(byte[] username, byte[] password) {
        return validate(new String(username, UTF_8), new String(password, UTF_8));
    }

    public abstract boolean validate(String username, String password);
}
