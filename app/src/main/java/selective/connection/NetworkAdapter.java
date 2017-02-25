package selective.connection;

import android.os.Handler;
import android.util.Log;

import java.nio.ByteBuffer;

import static selective.connection.SegmentManager.kSegHeaderSize;
import static selective.connection.SegmentManager.kSegRecv;
import static selective.connection.SegmentManager.kSegSend;
import static selective.connection.SegmentManager.kSegSize;

/**
 * Created by eslab on 2017-02-09.
 */

public abstract class NetworkAdapter {
    static public final int kDevOff = 0;
    static public final int kDevOn = 1;
    static public final int kDevDiscon = 2;
    static public final int kDevDisconnecting = 3;
    static public final int kDevConnecting = 4;
    static public final int kDevCon = 5;

    static public final int kATuninitialized = 0;
    static public final int kATInitialized = 1;
    static public final int kATCtrlable = 2;
    static public final int kATCtrl = 4;

    public abstract boolean device_on();
    public abstract boolean device_off();
    public abstract boolean make_connection();
    public abstract boolean close_connection();

    public abstract int send(byte[] buf, int len);
    public abstract int recv(byte[] buf, int len);
    public abstract void on_control_recv(byte[] buf, int len);

    private class SenderThread extends Thread {
        public void run() {
            SegmentManager sm = SegmentManager.get_instance();

            while (true) {
                Segment to_send = sm.dequeue(kSegSend);

                int len = kSegSize + kSegHeaderSize;
                byte[] data = to_send.data;
                int res = send(data, len);
                if (res < len) {
                    sm.enqueue(kSegSend, to_send);
                    if (res <= 0)
                        break;
                }

                sm.free_segment(to_send);
            }
        }
    }

    private class RecverThread extends Thread {
        public void run() {
            SegmentManager sm = SegmentManager.get_instance();
            Segment free_seg = sm.get_free_segment();

            while (true) {
                int len = kSegSize + kSegHeaderSize;
                //Log.d(tag, "Recving thread start:"+Integer.toString(dev_id));
                int res = recv(free_seg.data, len);
                if (res < len) {
                    Log.d(tag, "Recving failed");
                    sm.free_segment(free_seg);

                    if (res <= 0)
                        break;
                } else Log.d(tag, "Recved : " + Integer.toString(dev_id));

                ByteBuffer buffer = ByteBuffer.allocate(2);
                buffer.put(free_seg.data, 0, 2);
                free_seg.seq_no = buffer.getShort(0);
                //Log.d(tag, "Recved Seq No : " + Integer.toString(free_seg.seq_no));

                buffer = ByteBuffer.allocate(2);
                buffer.put(free_seg.data, 2, 2);
                free_seg.flag_len = buffer.getShort(0);

                sm.enqueue(kSegRecv, free_seg);
                free_seg = sm.get_free_segment();
            }
        }
    }

    private class ConnectingThread extends Thread {
        Handler mHandler;

        ConnectingThread(Handler handler) {
            mHandler = handler;
        }
        public void run() {
            if (stat != kDevConnecting)
                return;

            boolean res = make_connection();

            if (res) {
                if ((at & kATCtrl) == 0) {
                    if (send_thread != null)
                        send_thread.interrupt();

                    send_thread = new SenderThread();
                    send_thread.start();

                    if (recv_thread != null)
                        recv_thread.interrupt();

                    recv_thread = new RecverThread();
                    recv_thread.start();
                } else {
                    if (send_thread != null)
                        send_thread.interrupt();

                    if (recv_thread != null)
                        recv_thread.interrupt();

                    send_thread = null;
                    recv_thread = null;
                }

                stat = kDevCon;
            } else {
                stat = kDevDiscon;
            }

            mHandler.sendEmptyMessage(stat);
        }
    }

    private class ClosingThread extends Thread {
        public void run() {
            if (stat != kDevDisconnecting)
                return;

            boolean res = close_connection();

            if (res) stat = kDevDiscon;
            else stat = kDevCon;
        }
    }

    private String tag = "NetworkAdapter";

    private SenderThread send_thread;
    private RecverThread recv_thread;
    private ConnectingThread connect_thread;
    private ClosingThread close_thread;

    public short dev_id = 0;
    public int stat;
    private int at;

    NetworkAdapter() {
        at = kATuninitialized;
        stat = kDevDiscon;
        dev_id = 0;

        send_thread = null;
        recv_thread = null;
        connect_thread = null;
        close_thread = null;
    }

    public final void set_controllable() {
        at |= kATCtrlable;
    }

    public final void set_data_adapter() {
        if ((at & kATInitialized) == kATInitialized) {
            Log.d(tag, "Already initialized");
            return;
        }

        NetworkManager.get_instance().install_data_adapter(this);
        at |= kATInitialized;
    }

    public final void set_control_adapter() {
        if ((at & kATInitialized) == kATInitialized) {
            Log.d(tag, "Already initialized");
            return;
        }

        if ((at & kATCtrlable) == 0) {
            Log.d(tag, "Not controllable adapter");
            return;
        }
        at |= kATCtrl | kATInitialized;

        NetworkManager.get_instance().install_control_adapter(this);
    }

    public void connect(Handler handler) {
        if (at == kATuninitialized) {
            Log.d(tag, "The adapter has not been installed to communicator");
            return;
        }

        if (stat != kDevDiscon) {
            Log.d(tag, "The adapter is not disconnected");
            return;
        }

        stat = kDevConnecting;
        if (connect_thread != null) {
            connect_thread.interrupt();
        }

        connect_thread = new ConnectingThread(handler);
        connect_thread.start();
    }

    public void close(Handler handler) {
        if (stat != kDevCon) {
            Log.d(tag, "The adapter is not connected");
            return;
        }

        stat = kDevDisconnecting;
        if (close_thread != null) {
            close_thread.interrupt();
        }

        close_thread = new ClosingThread();
        close_thread.start();
    }

    public int get_stat() {
        return stat;
    }

    public void send_ctrl_msg(byte[] data, int len) {
        if ((at & kATCtrl) == kATCtrl) {
            Log.d(tag, "Cannot transfer private data in control adapter");
            return;
        }

        NetworkManager nm = NetworkManager.get_instance();
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte)NetworkManager.kCtrlReqPriv);

        nm.send_control_data(buffer.array(), 1);

        buffer = ByteBuffer.allocate(2);
        buffer.putShort(dev_id);
        nm.send_control_data(buffer.array(), 2);

        buffer = ByteBuffer.allocate(4);
        buffer.putInt(len);
        nm.send_control_data(buffer.array(), 4);

        nm.send_control_data(data, len);
    }
}
