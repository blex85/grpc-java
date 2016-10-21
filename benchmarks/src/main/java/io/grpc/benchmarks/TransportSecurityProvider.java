package io.grpc.benchmarks;

import java.security.Provider;
import org.conscrypt.OpenSSLProvider;

/**
 * The provider of TLS for the transport.
 */
public enum TransportSecurityProvider {
  NONE(null, new AllowAll(), "Use of plaintext (i.e. no TLS)."),
  DEFAULT(null, new AllowAll(), "Use the default JDK TLS provider."),
  NETTY_CONSCRYPT(new OpenSSLProvider(), new AllowNetty(),
      "Netty only. Overrides the default JDK security provider with Conscrypt."),
  NETTY_TCNATIVE(null, new AllowNetty(),
      "Netty only. Use the netty-tcnative wrapper around OpenSSL.");

  /**
   * Gets the Java security provider to be used.
   */
  public Provider provider() {
    return provider;
  }

  TransportSecurityProvider(Provider provider, TransportValidator validator, String description) {
    this.provider = provider;
    this.validator = validator;
    this.description = description;
  }

  private final Provider provider;
  private final TransportValidator validator;
  private final String description;

  /**
   * Indicates whether or not this TLS provider is valid for the given {@link Transport}.
   */
  public boolean isValidForTransport(Transport transport) {
    return validator.isValid(transport);
  }

  /**
   * Describe the {@link TransportSecurityProvider}.
   */
  public static String getDescriptionString() {
    StringBuilder builder = new StringBuilder("Select the TLS provider to use. Options:\n");
    boolean first = true;
    for (TransportSecurityProvider transport : TransportSecurityProvider.values()) {
      if (!first) {
        builder.append("\n");
      }
      builder.append(transport.name().toLowerCase());
      builder.append(": ");
      builder.append(transport.description);
      first = false;
    }
    return builder.toString();
  }

  private interface TransportValidator {

    boolean isValid(Transport transport);
  }

  private static final class AllowAll implements TransportValidator {

    @Override
    public boolean isValid(Transport transport) {
      return true;
    }
  }

  private static final class AllowNetty implements TransportValidator {

    @Override
    public boolean isValid(Transport transport) {
      switch (transport) {
        case NETTY_NIO:
        case NETTY_EPOLL:
        case NETTY_UNIX_DOMAIN_SOCKET:
          return true;
        default:
          return false;
      }
    }
  }
}
