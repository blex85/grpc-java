package io.grpc.transport.netty;

import io.grpc.Marshaller;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerImpl;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.testing.TestUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Verifies that the Netty client works properly in the Jetty container when using TLS with ALPN.
 */
@RunWith(JUnit4.class)
public class JettyTest {

  private org.eclipse.jetty.server.Server jettyServer;
  private ServerImpl grpcServer;
  private URL url;

  @Before
  public void setup() throws Exception {
    // Start the jetty server.
    jettyServer = new org.eclipse.jetty.server.Server();
    ServerConnector connector = new ServerConnector(jettyServer);
    connector.setPort(TestUtils.pickUnusedPort());
    jettyServer.setConnectors(new Connector[]{connector});
    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    context.addServlet(TestServlet.class, "/test");
    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[]{context, new DefaultHandler()});
    jettyServer.setHandler(handlers);
    jettyServer.start();

    url = new URL("http://localhost:" + connector.getPort() + "/test");

    // Build and start the gRPC server.
    int grpcServerPort = TestUtils.pickUnusedPort();
    File cert = TestUtils.loadCert("server1.pem");
    File key = TestUtils.loadCert("server1.key");
    NettyServerBuilder builder = NettyServerBuilder.forPort(grpcServerPort);
    grpcServer = builder.addService(ServerServiceDefinition.builder("jettyTest").addMethod("unary",
            new ByteBufOutputMarshaller(),
            new ByteBufOutputMarshaller(),
            new CallHandler()).build()).build();
    grpcServer.start();
  }

  @After
  public void teardown() throws Exception {
    grpcServer.shutdownNow();
    jettyServer.stop();
    // TODO(nathanmittler): stop the grpc server.
  }

  @Test
  public void test() throws Exception {
    // TODO(nathanmittler): send a GET request to the servlet and wait for the response.
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
    try {
      InputStream in = urlConnection.getInputStream();
      drain(in);
    } finally {
      urlConnection.disconnect();
    }
  }

  private static void drain(InputStream in) throws IOException {
    byte[] buf = new byte[1024];
    while ((in.read(buf)) >= 0) {
      // Just keep reading.
    }
  }

  public static class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
      // TODO(nathanmittler): Send a gRPC request to the server.
      System.err.println("received a GET request");
      try {
        Class.forName("org.eclipse.jetty.alpn.ALPN");
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
      resp.getWriter().write("whatever");
    }
  }

  private static class CallHandler implements ServerCallHandler<ByteBuf, ByteBuf> {
    @Override
    public ServerCall.Listener<ByteBuf> startCall(String fullMethodName,
                                                  final ServerCall<ByteBuf> call,
                                                  Metadata.Headers headers) {
      call.request(1);
      return new ServerCall.Listener<ByteBuf>() {
        @Override
        public void onPayload(ByteBuf payload) {
          // no-op
          payload.release();
          call.sendPayload(Unpooled.EMPTY_BUFFER);
        }

        @Override
        public void onHalfClose() {
          call.close(Status.OK, new Metadata.Trailers());
        }

        @Override
        public void onCancel() {
        }

        @Override
        public void onComplete() {
        }
      };
    }
  }

  /**
   * Simple {@link io.grpc.Marshaller} for Netty ByteBuf.
   */
  protected static class ByteBufOutputMarshaller implements Marshaller<ByteBuf> {

    public static final EmptyByteBuf EMPTY_BYTE_BUF =
            new EmptyByteBuf(PooledByteBufAllocator.DEFAULT);

    protected ByteBufOutputMarshaller() {
    }

    @Override
    public InputStream stream(ByteBuf value) {
      return new ByteBufInputStream(value);
    }

    @Override
    public ByteBuf parse(InputStream stream) {
      try {
        // We don't do anything with the payload and it's already been read into buffers
        // so just skip copying it.
        int available = stream.available();
        if (stream.skip(available) != available) {
          throw new RuntimeException("Unable to skip available bytes.");
        }
        return EMPTY_BYTE_BUF;
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
  }
}
