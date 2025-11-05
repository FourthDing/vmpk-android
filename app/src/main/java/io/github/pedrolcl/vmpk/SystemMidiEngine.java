/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */
/* Copyright © 2025 Alvin Wong */

package io.github.pedrolcl.vmpk;

import static android.content.Context.MIDI_SERVICE;
import static android.content.pm.PackageManager.FEATURE_MIDI;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Objects;
import java.util.Queue;

public class SystemMidiEngine implements MidiEngine, MidiManager.OnDeviceOpenedListener {
    private static final String TAG = "SystemMidiEngine";

    private Activity mActivity;

    private ConnectionListener mConnectionListener;

    private MidiManager mMidiManager;

    private final MidiDeviceCallback mMidiDeviceCallback = new MidiDeviceCallback();

    private static final int PORT_MENU_GROUP_ID = 1;

    private SubMenu mMidiPortsSubMenu;
    private MenuItem mMidiPortsDisconnectMenuItem;

    private HashMap<MenuItem, MidiInputPortInfo> mMenuItemToMidiPortMap = new HashMap<>();

    private class MidiInputPortInfo {
        private MidiDeviceInfo mDeviceInfo;
        private int mPortNumber;

        public MidiInputPortInfo(MidiDeviceInfo mDeviceInfo, int mPortNumber) {
            this.mDeviceInfo = mDeviceInfo;
            this.mPortNumber = mPortNumber;
        }

        public MidiDeviceInfo getDeviceInfo() {
            return mDeviceInfo;
        }

        public void setDeviceInfo(MidiDeviceInfo mDeviceInfo) {
            this.mDeviceInfo = mDeviceInfo;
        }

        public int getPortNumber() {
            return mPortNumber;
        }

        public void setPortNumber(int mPortNumber) {
            this.mPortNumber = mPortNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            MidiInputPortInfo that = (MidiInputPortInfo) o;
            return mPortNumber == that.mPortNumber && Objects.equals(mDeviceInfo, that.mDeviceInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDeviceInfo, mPortNumber);
        }
    }

    private MidiInputPortInfo mUseMidiPortInfo;

    private MidiDevice mMidiDevice;
    private MidiInputPort mMidiInputPort;

    private boolean mMidiDevicePending = false;
    private Queue<byte[]> mMidiDataQueue = new ArrayDeque<>();

    private SystemMidiEngine(Activity activity, ConnectionListener connectionListener, MidiManager midiManager) {
        mActivity = activity;
        mConnectionListener = connectionListener;
        mMidiManager = midiManager;
    }

    public static SystemMidiEngine create(Activity activity, ConnectionListener connectionListener) {
        if (!activity.getPackageManager().hasSystemFeature(FEATURE_MIDI)) {
            Log.e(TAG, "System does not support MIDI feature.");
            return null;
        }

        MidiManager midiManager = (MidiManager) activity.getSystemService(MIDI_SERVICE);
        if (midiManager == null) {
            Log.e(TAG, "MidiManager is null");
            return null;
        }

        return new SystemMidiEngine(activity, connectionListener, midiManager);
    }

    @Override
    public void configureOptionsMenu(Menu menu) {
        mMidiPortsSubMenu = menu.addSubMenu(Menu.NONE, Menu.NONE, 90, R.string.action_midi_port);
        mMidiPortsSubMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        populateMidiPortList();
    }

    private void populateMidiPortList() {
        if (mMidiPortsSubMenu == null) {
            return;
        }
        mMidiPortsSubMenu.clear();
        mMenuItemToMidiPortMap.clear();

        mMidiPortsDisconnectMenuItem = mMidiPortsSubMenu.add(PORT_MENU_GROUP_ID, 1, Menu.NONE, R.string.action_midi_port_disconnected);
        mMidiPortsDisconnectMenuItem.setCheckable(true);
        if (mUseMidiPortInfo == null) {
            mMidiPortsDisconnectMenuItem.setChecked(true);
        }

        for (MidiDeviceInfo device : mMidiManager.getDevices()) {
            addMidiDeviceToPortList(device);
        }

        mMidiPortsSubMenu.setGroupCheckable(PORT_MENU_GROUP_ID, true, true);
    }

    private void addMidiDeviceToPortList(MidiDeviceInfo deviceInfo) {
        if (deviceInfo.getInputPortCount() < 1) {
            return;
        }

        for (MidiDeviceInfo.PortInfo port : deviceInfo.getPorts()) {
            if (port.getType() == MidiDeviceInfo.PortInfo.TYPE_INPUT) {
                addMidiDevicePortToPortList(deviceInfo, port);
            }
        }
    }

    private void addMidiDevicePortToPortList(MidiDeviceInfo deviceInfo, MidiDeviceInfo.PortInfo portInfo) {
        Bundle bundle = deviceInfo.getProperties();
        StringBuilder sb = new StringBuilder();
        String name = bundle.getString(MidiDeviceInfo.PROPERTY_NAME);
        if (name != null && !name.isEmpty()) {
            sb.append(name);
        } else {
            sb.append(bundle.getString(MidiDeviceInfo.PROPERTY_PRODUCT));
            sb.append(", ");
            sb.append(bundle.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER));
        }
        String serial = bundle.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER);
        if (serial != null && !serial.isEmpty()) {
            sb.append(" (");
            sb.append(serial);
            sb.append(')');
        }
        sb.append(" [");
        sb.append(portInfo.getPortNumber());
        if (portInfo.getName() != null && !portInfo.getName().isEmpty()) {
            sb.append(':');
            sb.append(portInfo.getName());
        }
        sb.append(']');
        String displayText = sb.toString();

        MenuItem newItem = mMidiPortsSubMenu.add(PORT_MENU_GROUP_ID, mMenuItemToMidiPortMap.size() + 2, Menu.NONE, displayText);
        MidiInputPortInfo port = new MidiInputPortInfo(deviceInfo, portInfo.getPortNumber());
        mMenuItemToMidiPortMap.put(newItem, port);

        newItem.setCheckable(true);
        if (port.equals(mUseMidiPortInfo)) {
            newItem.setChecked(true);
        }
    }

    private void removeMidiDeviceFromPortList(MidiDeviceInfo deviceInfo) {
        mMenuItemToMidiPortMap.entrySet().removeIf(entry -> {
            if (entry.getValue().getDeviceInfo().equals(deviceInfo)) {
                mMidiPortsSubMenu.removeItem(entry.getKey().getItemId());
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == mMidiPortsDisconnectMenuItem) {
            item.setChecked(true);
            closeMidiPort();
            mUseMidiPortInfo = null;
            return true;
        }

        MidiInputPortInfo port = mMenuItemToMidiPortMap.getOrDefault(item, null);
        if (port != null) {
            mMidiPortsDisconnectMenuItem.setChecked(true);
            closeMidiPort();
            mUseMidiPortInfo = port;
            openMidiPort();
            item.setChecked(true);
            if (mConnectionListener != null) {
                mConnectionListener.onMidiConnected();
            }
            return true;
        }
        return false;
    }

    @Override
    public void start(Activity activity) {
        populateMidiPortList();
        mMidiManager.registerDeviceCallback(mMidiDeviceCallback, new Handler(Looper.getMainLooper()));

        openMidiPort();
    }

    private void openMidiPort() {
        if (mUseMidiPortInfo != null) {
            try {
                mMidiManager.openDevice(mUseMidiPortInfo.getDeviceInfo(), this, new Handler(Looper.getMainLooper()));
            } catch (IllegalArgumentException e) {
                mUseMidiPortInfo = null;
                if (mMidiPortsDisconnectMenuItem != null) {
                    mMidiPortsDisconnectMenuItem.setChecked(true);
                }

                new AlertDialog.Builder(mActivity)
                        .setTitle(R.string.dialog_midi_device_error_title)
                        .setMessage(R.string.dialog_midi_device_error_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .show();
                return;
            }
            mMidiDevicePending = true;
        }
    }

    @Override
    public void onDeviceOpened(MidiDevice midiDevice) {
        mMidiDevicePending = false;
        mMidiDevice = midiDevice;
        mMidiInputPort = midiDevice.openInputPort(mUseMidiPortInfo.getPortNumber());
        if (mMidiInputPort == null) {
            Log.e(TAG, "Failed to open MIDI port");
            mUseMidiPortInfo = null;
            closeMidiPort();
            if (mMidiPortsDisconnectMenuItem != null) {
                mMidiPortsDisconnectMenuItem.setChecked(true);
            }

            new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.dialog_midi_port_error_title)
                    .setMessage(R.string.dialog_midi_port_error_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create()
                    .show();
            return;
        }

        byte[] data;
        while ((data = mMidiDataQueue.poll()) != null) {
            Log.d(TAG, "replaying queued packet");
            sendMidi(data);
        }
    }

    @Override
    public void stop() {
        closeMidiPort();

        mMidiManager.unregisterDeviceCallback(mMidiDeviceCallback);
    }

    private void closeMidiPort() {
        if (mMidiInputPort != null) {
            MidiInputPort midiInputPort = mMidiInputPort;
            mMidiInputPort = null;
            try {
                midiInputPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error when closing MidiInputPort", e);
            }
        }

        if (mMidiDevice != null) {
            MidiDevice midiDevice = mMidiDevice;
            mMidiDevice = null;
            try {
                midiDevice.close();
            } catch (IOException e) {
                Log.e(TAG, "Error when closing MidiDevice", e);
            }
        }

        mMidiDevicePending = false;
        mMidiDataQueue.clear();
    }

    private void sendMidi(byte[] data) {
        if (mMidiInputPort == null) {
            if (mMidiDevicePending) {
                mMidiDataQueue.add(data);
                Log.d(TAG, "queued packet");
            } else {
                Log.d(TAG, "discarded packet");
            }
            return;
        }

        try {
            mMidiInputPort.send(data, 0, data.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void sendMidi(int m, int n, int v) {
        byte msg[] = new byte[3];
        msg[0] = (byte) m;
        msg[1] = (byte) n;
        msg[2] = (byte) v;
        sendMidi(msg);
    }

    protected void sendMidi(int m, int n) {
        byte msg[] = new byte[2];
        msg[0] = (byte) m;
        msg[1] = (byte) n;
        sendMidi(msg);
    }

    @Override
    public void pitchWheel(int channel, int num) {
        // num >= 0, num <= 16384
        int lsb = num % 0x80;
        int msb = num / 0x80;
        sendMidi(STATUS_BENDER | channel, lsb, msb);
    }

    @Override
    public void channelPressure(int channel, int num) {
        sendMidi(STATUS_CHANAFT | channel, num);
    }

    @Override
    public void programChange(int channel, int num) {
        sendMidi(STATUS_PROGRAM | channel, num);
    }

    @Override
    public void controller(int channel, int ctl, int num) {
        sendMidi(STATUS_CTLCHG | channel, ctl, num);
    }

    @Override
    public void aftertouch(int channel, int note, int num) {
        sendMidi(STATUS_POLYAFT | channel, note, num);
    }

    @Override
    public void noteOn(int channel, int note, int vel) {
        sendMidi(STATUS_NOTEON | channel, note, vel);
    }

    @Override
    public void noteOff(int channel, int note, int vel) {
        sendMidi(STATUS_NOTEOFF | channel, note, vel);
    }

    @Override
    public void panic() {
        for (int ch = 0; ch < 16; ++ch) {
            controller(ch, CTL_ALL_NOTES_OFF, 0);
        }
    }

    @Override
    public void reset() {
        for (int ch = 0; ch < 16; ++ch) {
            controller(ch, CTL_RESET_ALL_CTL, 0);
        }
    }

    private class MidiDeviceCallback extends MidiManager.DeviceCallback {
        @Override
        public void onDeviceAdded(MidiDeviceInfo device) {
            addMidiDeviceToPortList(device);
            mMidiPortsSubMenu.setGroupCheckable(PORT_MENU_GROUP_ID, true, true);
            Log.d(TAG, "device added");
        }

        @Override
        public void onDeviceRemoved(MidiDeviceInfo device) {
            removeMidiDeviceFromPortList(device);
            if (mUseMidiPortInfo != null && device.equals(mUseMidiPortInfo.getDeviceInfo())) {
                closeMidiPort();
                mUseMidiPortInfo = null;
                if (mMidiPortsDisconnectMenuItem != null) {
                    mMidiPortsDisconnectMenuItem.setChecked(true);
                }

                new AlertDialog.Builder(mActivity)
                        .setTitle(R.string.dialog_midi_device_error_title)
                        .setMessage(R.string.dialog_midi_device_error_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .show();
                return;
            }
            Log.d(TAG, "device removed");
        }

        @Override
        public void onDeviceStatusChanged(MidiDeviceStatus status) {
            Log.d(TAG, "device status changed");
        }
    }

}
