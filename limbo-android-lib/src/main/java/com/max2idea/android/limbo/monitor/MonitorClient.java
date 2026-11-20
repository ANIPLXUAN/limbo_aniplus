package com.max2idea.android.limbo.monitor;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.InputStream;
import java.io.OutputStream;

public class MonitorClient {
    private static LocalSocket socket;
    private static OutputStream out;
    private static InputStream in;

    public static boolean connect(String path) {
        try {
            socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(path,
                    LocalSocketAddress.Namespace.FILESYSTEM));
            out = socket.getOutputStream();
            in = socket.getInputStream();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    public static String sendCommand(String cmd) {
        if (socket == null || !socket.isConnected()) {
            return "未连接";
        }
        try {
            // 1. 先清空残留数据
            drain();

            // 2. 发送命令
            out.write((cmd + "\n").getBytes());
            out.flush();
            Thread.sleep(500);

            // 3. 读结果
            socket.setSoTimeout(2000);
            byte[] buf = new byte[8192];
            try {
                int n = in.read(buf);
                if (n > 0) {
                    String raw = new String(buf, 0, n);
                    return stripAnsi(raw);
                }
                return "(无输出)";
            } catch (java.net.SocketTimeoutException e) {
                return "(超时，QEMU 未响应)";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /** 把 socket 缓冲里的残留数据读掉，避免历史输出堆积 */
    private static void drain() {
        try {
            socket.setSoTimeout(50);
            byte[] discard = new byte[8192];
            while (in.read(discard) > 0) {
                // 丢弃
            }
        } catch (java.net.SocketTimeoutException ignored) {
            // 没数据了，正常退出
        } catch (Exception ignored) {
        }
    }

    /** 去掉 ANSI 转义序列和控制字符 */
    private static String stripAnsi(String s) {
        if (s == null) return "";
        // 去 ANSI 转义序列
        s = s.replaceAll("\u001B\\[[;\\d]*[A-Za-z]", "");
        // 去 readline 的控制字符（[K、[D 等）
        s = s.replaceAll("\\[K", "");
        s = s.replaceAll("\\[D", "");
        s = s.replaceAll("\\[[0-9;]*[A-Za-z]", "");
        // 去其他控制字符，保留 \n \t
        s = s.replaceAll("[\\x00-\\x08\\x0B-\\x1F\\x7F]", "");
        return s.trim();
    }

    public static void disconnect() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
    }
}