package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.spotifyprovider.TokenRefresher.TokenValues;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

class LocalServerReceiver {

  @Nonnull
  private static final Logger log = Logger.getLogger(LocalServerReceiver.class.getName());

  private static final String STATE_KEY = "state";
  private static final String ACCESS_TOKEN_KEY = "access_token";
  private static final String EXPIRATION_KEY = "expires_in";

  private static final String CALLBACK_PATH = "/Callback";
  private static final String LOCALHOST = "localhost";

  private final Server server;
  private final Lock lock;
  private final Condition done;

  private final URL redirectUrl;
  private final String state;

  private boolean received = false;
  private TokenValues token;

  public LocalServerReceiver(int port, String state) throws IOException {
    this.lock = new ReentrantLock();
    this.done = lock.newCondition();

    this.redirectUrl = new URL("http", LOCALHOST, port, CALLBACK_PATH);
    this.state = state;

    this.server = new Server(port);
    for (Connector c : server.getConnectors()) {
      c.setHost(LOCALHOST);
    }
    server.setHandler(new Callback());
    try {
      server.start();
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public URL getRedirectUrl() {
    return redirectUrl;
  }

  private void stop() {
    try {
      server.setGracefulShutdown(500);
      server.stop();
      log.finer("Jetty server stopped.");
    } catch (Exception e) {
      log.severe("Could not close server: " + e);
    }
  }

  private TokenValues waitForToken(InterruptableRunnable wait) throws InterruptedException {
    lock.lock();
    try {
      if (!received) {
        wait.run();
      }
      stop();
      return token;
    } finally {
      lock.unlock();
    }
  }

  public TokenValues waitForToken() throws InterruptedException {
    return waitForToken(done::await);
  }

  public TokenValues waitForToken(long timeout, TimeUnit unit) throws InterruptedException {
    return waitForToken(() -> done.await(timeout, unit));
  }

  private class Callback extends AbstractHandler {

    @Override
    public void handle(String target, HttpServletRequest request, HttpServletResponse response,
        int dispatch) throws IOException {
      log.finer("Handle...");
      if (!CALLBACK_PATH.equals(target)) {
        log.warning("Wrong path: " + target);
        return;
      }

      if (request.getParameterMap().containsKey(ACCESS_TOKEN_KEY)) {
        if (!state.equals(request.getParameter(STATE_KEY))) {
          log.warning("Wrong state: " + request.getParameter(STATE_KEY));
          return;
        }
        log.finer("Writing landing page...");
        writeLandingHtml(response);
        response.flushBuffer();
        lock.lock();
        try {
          received = true;
          String accessToken = request.getParameter(ACCESS_TOKEN_KEY);
          String expiresIn = request.getParameter(EXPIRATION_KEY);
          long expiration = (Integer.parseUnsignedInt(expiresIn) - 600) * 1000;
          Date expirationDate = new Date(System.currentTimeMillis() + expiration);
          token = new TokenValues(accessToken, expirationDate);
          done.signalAll();
        } catch (Exception e) {
          throw new IOException(e);
        } finally {
          lock.unlock();
        }
      } else {
        log.fine("Redirecting...");
        writeRedirectHtml(response);
        response.flushBuffer();
      }

      ((Request) request).setHandled(true);
    }

    private void writeLandingHtml(HttpServletResponse response) throws IOException {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("text/html");

      PrintWriter doc = response.getWriter();
      doc.println("<html>");
      doc.println("<head><title>OAuth 2.0 Authentication Token Received</title></head>");
      doc.println("<body>");
      doc.println("Received verification code. You may now close this window...");
      doc.println("</body>");
      doc.println("</HTML>");
      doc.flush();
    }

    private void writeRedirectHtml(HttpServletResponse response) throws IOException {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("text/html");

      PrintWriter doc = response.getWriter();
      doc.println("<html>");
      doc.println("<head><title>OAuth 2.0 Authentication Token Received</title></head>");
      doc.println("<body>");
      doc.println("Redirecting...");
      doc.println("<script>");
      doc.println("if(window.location.hash) {");
      doc.println("window.open('"
          + redirectUrl.toExternalForm()
          + "?' + window.location.hash.substring(1), '_self', false);");
      doc.println("}");
      doc.println("</script>");
      doc.println("</body>");
      doc.println("</HTML>");
      doc.flush();
    }
  }

  @FunctionalInterface
  private interface InterruptableRunnable {

    void run() throws InterruptedException;
  }
}
