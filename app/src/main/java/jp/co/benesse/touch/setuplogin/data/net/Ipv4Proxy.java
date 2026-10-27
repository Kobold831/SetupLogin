package jp.co.benesse.touch.setuplogin.data.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Ipv4Proxy {

    private static final int CONNECT_TIMEOUT = 15000;

    private static final Map<String, InetAddress> RESOLVED = new ConcurrentHashMap<>();

    private static ServerSocket serverSocket;
    private static Proxy proxy = Proxy.NO_PROXY;

    private Ipv4Proxy() {
    }

    public static void prefetch(String url) {
        try {
            resolveIpv4(new java.net.URL(url).getHost());
        } catch (Exception ignored) {
        }
    }

    public static synchronized Proxy get() {
        if (serverSocket == null) {
            try {
                ServerSocket socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
                serverSocket = socket;
                Thread acceptor = new Thread(() -> acceptLoop(socket), "ipv4-proxy");
                acceptor.setDaemon(true);
                acceptor.start();
                proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", socket.getLocalPort()));
            } catch (IOException ignored) {
                proxy = Proxy.NO_PROXY;
            }
        }
        return proxy;
    }

    private static void acceptLoop(ServerSocket server) {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                Thread handler = new Thread(() -> handle(client), "ipv4-proxy-conn");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private static void handle(Socket client) {
        Socket remote = null;
        try {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            if (readByte(in) != 5) {
                return;
            }
            skip(in, readByte(in));
            out.write(new byte[]{5, 0});
            out.flush();

            if (readByte(in) != 5) {
                return;
            }
            int command = readByte(in);
            skip(in, 1);
            int addressType = readByte(in);

            String host;
            if (addressType == 1) {
                host = InetAddress.getByAddress(readBytes(in, 4)).getHostAddress();
            } else if (addressType == 3) {
                host = new String(readBytes(in, readByte(in)), StandardCharsets.UTF_8);
            } else {
                reply(out, 8);
                return;
            }
            int port = (readByte(in) << 8) | readByte(in);

            if (command != 1) {
                reply(out, 7);
                return;
            }

            InetAddress target = resolveIpv4(host);
            if (target == null) {
                reply(out, 4);
                return;
            }

            remote = new Socket();
            remote.connect(new InetSocketAddress(target, port), CONNECT_TIMEOUT);
            reply(out, 0);

            Socket target2 = remote;
            Thread upstream = new Thread(() -> {
                try {
                    copy(client.getInputStream(), target2.getOutputStream(), client, target2);
                } catch (IOException ignored) {
                }
            }, "ipv4-proxy-upstream");
            upstream.setDaemon(true);
            upstream.start();
            copy(remote.getInputStream(), client.getOutputStream(), client, remote);
        } catch (IOException ignored) {
        } finally {
            closeQuietly(client);
            closeQuietly(remote);
        }
    }

    private static InetAddress resolveIpv4(String host) {
        InetAddress cached = RESOLVED.get(host);
        if (cached != null) {
            return cached;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address instanceof Inet4Address) {
                    RESOLVED.put(host, address);
                    return address;
                }
            }
        } catch (UnknownHostException ignored) {
        }
        return null;
    }

    private static void reply(OutputStream out, int code) throws IOException {
        out.write(new byte[]{5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0});
        out.flush();
    }

    private static int readByte(InputStream in) throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException();
        }
        return value;
    }

    private static byte[] readBytes(InputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new EOFException();
            }
            offset += read;
        }
        return bytes;
    }

    private static void skip(InputStream in, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            readByte(in);
        }
    }

    private static void copy(InputStream in, OutputStream out, Socket first, Socket second) {
        try {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) >= 0) {
                out.write(buffer, 0, length);
            }
            out.flush();
        } catch (IOException ignored) {
        } finally {
            closeQuietly(first);
            closeQuietly(second);
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
