package com.gwt.BLE.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gwt.BLE.R;
import com.gwt.BLE.data.DataBaseHelper;
import com.gwt.BLE.data.Device;
import com.gwt.BLE.data.Visitor;
import com.ublox.BLE.interfaces.BluetoothDeviceRepresentation;
import com.ublox.BLE.services.BluetoothLeService;
import com.ublox.BLE.utils.ConnectionState;
import com.ublox.BLE.utils.GattAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static com.ublox.BLE.services.BluetoothLeService.ITEM_TYPE_NOTIFICATION;
import static com.ublox.BLE.services.BluetoothLeService.ITEM_TYPE_READ;
import static com.ublox.BLE.services.BluetoothLeService.ITEM_TYPE_WRITE;
import static java.lang.Math.min;

public class MainActivity extends Activity {

    private static final String TAG = "MainBLE";

    public static final String EXTRA_DEVICE = "device";

    private boolean editMode = false;
    private final VisitorListActivity visitorListActivity = new VisitorListActivity();
    private final VisitorEditActivity visitorEditActivity = new VisitorEditActivity();
    boolean isEditActivityCreated = false;

    private BluetoothDeviceRepresentation mDevice;

    private BluetoothLeService mBleService;
    private static ConnectionState mConnectionState = ConnectionState.DISCONNECTED;
    public final BroadcastReceiver mGatt = new BroadcastReceiver();

    private DataBaseHelper db;
    private Device dbDevice;

    ArrayList<Visitor> visitors;
    ArrayList<Integer> visitorsPermitted;
    private int currentVisitorIdx = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        visitors = new ArrayList<>();
        visitorsPermitted = new ArrayList<>();

        editMode = false;
        visitorListActivity.onCreate();

        final Intent intent = getIntent();
        mDevice = intent.getParcelableExtra(EXTRA_DEVICE);
        if (mDevice != null) {
            connectToDevice();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        db = new DataBaseHelper(getApplicationContext());
        if (mDevice != null) {
            dbDevice = db.getDevice(mDevice.getAddress());
            if (dbDevice != null) {
                dbDevice.setName(mDevice.getName());
            } else {
                dbDevice = new Device(mDevice.getName(), mDevice.getAddress(), false);
            }
        }

        if (mDevice != null && mBleService != null) {
            mBleService.register(mGatt);
            final boolean result = mBleService.connect(mDevice);
            Log.d(TAG, "Connect request result=" + result);
            mConnectionState = ConnectionState.CONNECTING;
            invalidateOptionsMenu();
            if (!editMode) {
                visitorListActivity.updateStatus();
                visitorListActivity.rlProgress.setVisibility(View.VISIBLE);
            }
        }

        if (editMode) {
            visitorEditActivity.onStart();
        } else {
            visitorListActivity.onStart();
        }
        switchViews(); // show active view
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editMode) {
            visitorEditActivity.onResume();
        } else {
            visitorListActivity.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (editMode) {
            visitorEditActivity.onPause();
        } else {
            visitorListActivity.onPause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isEditActivityCreated) {
            visitorEditActivity.onStop();
        }
        visitorListActivity.onStop();

        if (mDevice != null) {
            try {
                mGatt.stopHBTimer();
                mGatt.stopConnectionTimer();
                if (mConnectionState == ConnectionState.CONNECTED) {
                    mGatt.sendBleExit();
                }
                mBleService.disconnect();
                mBleService.close();
                mConnectionState = ConnectionState.DISCONNECTED;
                mBleService.unregister();
            } catch (Exception ignore) {
            }

            db.updateDevice(dbDevice);
        }

        db.closeDB();

        invalidateOptionsMenu();
    }

    @Override
    public void onBackPressed() {
        if (editMode) {
            visitorEditActivity.onBackPressed();
        } else {
            visitorListActivity.onBackPressed();
        }
    }

    private void connectToDevice() {
        // get the information from the device scan
        mBleService = new BluetoothLeService();
        if (!mBleService.initialize(this)) {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (editMode) {
            return visitorEditActivity.onCreateOptionsMenu(menu);
        } else {
            return visitorListActivity.onCreateOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean res;
        if (editMode) {
            res = visitorEditActivity.onOptionsItemSelected(item);
        } else {
            res = visitorListActivity.onOptionsItemSelected(item);
        }
        if (res) {
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void setAppTitle(final String title) {
        final ActionBar actionBar = getActionBar();
        if (title != null) {
            actionBar.setTitle(title);
            return;
        }

        if (mDevice != null) {
            final String name = mDevice.getName();
            actionBar.setTitle(TextUtils.isEmpty(name) ? mDevice.getAddress() : name);
        } else {
            actionBar.setTitle("");
        }
    }

    private void switchViews() {
        final ActionBar actionBar = getActionBar();
        final LinearLayout llMain = findViewById(R.id.llMain);
        final LinearLayout editMain = findViewById(R.id.editMain);

        if (editMode) {
            if (!isEditActivityCreated) {
                visitorEditActivity.onCreate();
                visitorEditActivity.onStart();
            }
            visitorListActivity.onPause();
            visitorEditActivity.onResume();
            llMain.setVisibility(View.GONE);
            editMain.setVisibility(View.VISIBLE);

            // TODO: set new theme
            //actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.ublox_blue_color)));
            setAppTitle((mConnectionState == ConnectionState.DISCONNECTED ||
                         mConnectionState == ConnectionState.DISCONNECTING) ? "" : null);
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setIcon(R.drawable.ic_close_black_24dp);
        } else {
            visitorEditActivity.onPause();
            visitorListActivity.onResume();
            editMain.setVisibility(View.GONE);
            llMain.setVisibility(View.VISIBLE);

            setAppTitle(null);
            actionBar.setLogo(R.drawable.logo);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
        }

        invalidateOptionsMenu();
    }

    private class VisitorListActivity {

        private static final String TAG = "VisitorList";

        private TextView tvStatus;
        private RelativeLayout rlProgress;
        private PeopleListAdapter peopleAdapter;

        class PeopleListAdapter extends BaseAdapter {
            Context context;
            LayoutInflater inflater;
            ArrayList<Visitor> people;
            ArrayList<Integer> peoplePermitted; // Indicates that person has access to currently connected device

            private PeopleListAdapter(Context context, ArrayList<Visitor> people, ArrayList<Integer> peoplePermitted)
            {
                this.context = context;
                this.people = people;
                this.peoplePermitted = peoplePermitted;
                inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public int getCount() {
                if (people == null) {
                    return 0;
                } else {
                    return people.size();
                }
            }

            @Override
            public Object getItem(int position) {
                if (people == null) {
                    return  null;
                }

                return people.get(position);
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = inflater.inflate(R.layout.listitem_person, parent, false);
                }

                Visitor person = people.get(position);

                TextView nameTextView = view.findViewById(R.id.person_name);
                nameTextView.setText(person.getName());

                TextView descriptionTextView = view.findViewById(R.id.person_description);
                descriptionTextView.setText(person.getDescription());

                ImageView personPreview = view.findViewById(R.id.person_photo);
                Bitmap photoPreview = person.getPhoto();
                if (photoPreview != null) {
                    personPreview.setImageBitmap(photoPreview);
                } else {
                    personPreview.setImageResource(R.drawable.ic_unknown_person);
                }

                ImageView accessIndicator = view.findViewById(R.id.person_indicator);
                if (peoplePermitted.contains(person.getId())) {
                    accessIndicator.setVisibility(View.VISIBLE);
                } else {
                    accessIndicator.setVisibility(View.INVISIBLE);
                }

                if (mDevice != null) {
                    Visitor.Access access = person.getAccess(mDevice.getAddress());
                    if ((person.getOldName() != null) && !person.getName().equals(person.getOldName()) ||
                        (access != null && access.granted != access.oldGranted)) {
                        nameTextView.setTypeface(null, Typeface.BOLD_ITALIC);
                    } else {
                        nameTextView.setTypeface(null, Typeface.BOLD);
                    }
                }

                return view;
            }
        }

        void onCreate() {
            tvStatus = findViewById(R.id.tvStatus);
            rlProgress = findViewById(R.id.rlProgress);

            peopleAdapter = new PeopleListAdapter(MainActivity.this, visitors, visitorsPermitted);
            ListView peopleListView = findViewById(R.id.person_list);
            peopleListView.setAdapter(peopleAdapter);

            peopleListView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
                currentVisitorIdx = position;
                editMode = true;
                switchViews();
            });
        }

        void onStart() {
            ArrayList<Visitor> dbVisitors = db.getAllVisitors(/*mDevice.getAddress()*/);
            if (visitors.isEmpty()) {
                visitors.addAll(dbVisitors);
            } else { // merge two lists
                int listSize = visitors.size();
                for (Visitor v : dbVisitors) {
                    int i;
                    for (i = 0; i < listSize; i++) {
                        if (visitors.get(i).getId() == v.getId()) {
                            break;
                        }
                    }
                    if (i == listSize) {
                        visitors.add(v);
                    }
                }
            }
        }

        void onResume() {
            updateStatus();
        }

        void onPause() { }

        void onStop() { }

        void onBackPressed() {
            Log.v(TAG, "onBackPressed()");
            if (mDevice != null) {
                if (mConnectionState == ConnectionState.CONNECTED) {
                    mGatt.stopHBTimer();
                    mGatt.stopConnectionTimer();
                    mGatt.sendBleExit();
                    mConnectionState = ConnectionState.DISCONNECTING;
                    invalidateOptionsMenu();
                    updateStatus();
                }
            }
            MainActivity.super.onBackPressed();
        }

        boolean onOptionsItemSelected(MenuItem item) {
            Log.v(TAG, "onOptionsItemSelected()");
            switch (item.getItemId()) {
                case R.id.menu_connect:
                    mBleService.connect(mDevice);
                    mConnectionState = ConnectionState.CONNECTING;
                    invalidateOptionsMenu();
                    updateStatus();
                    rlProgress.setVisibility(View.VISIBLE);
                    return true;
                case R.id.menu_disconnect:
                    mGatt.stopHBTimer();
                    if (mConnectionState == ConnectionState.CONNECTED) {
                        mGatt.sendBleExit();
                        mConnectionState = ConnectionState.DISCONNECTING;
                    } else {
                        mBleService.disconnect();
                    }

                    invalidateOptionsMenu();
                    updateStatus();
                    rlProgress.setVisibility(View.VISIBLE);
                    return true;
                case R.id.menu_refresh:
                    if (mBleService != null) {
                        visitorsPermitted.clear();
                        for (int i = 0; i < visitors.size(); i++) {
                            Visitor v = visitors.get(i);
                            Visitor.Access access = v.getAccess(mDevice.getAddress());
                            if (access != null) {
                                access.oldGranted = false;
                            }
                            v.setOldName(null);
                        }

                        peopleAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Starting people enumeration on device");
                        mGatt.sendBleReadVisitor();
                        mConnectionState = ConnectionState.BLE_EXCHANGE_READ;
                        invalidateOptionsMenu();
                        updateStatus();
                        //rlProgress.setVisibility(View.VISIBLE);
                    }
                    return true;
                case R.id.menu_favourite:
                    if (dbDevice.isFavourite()) {
                        dbDevice.setFavourite(false);
                        item.setIcon(R.drawable.ic_star_border_black_24dp);
                    } else {
                        dbDevice.setFavourite(true);
                        item.setIcon(R.drawable.ic_star_black_24dp);
                    }
                    return true;
                case R.id.menu_device_list:
                case android.R.id.home:
                    onBackPressed();
                    return true;
            }
            return false;
        }

        boolean onCreateOptionsMenu(Menu menu) {
            getMenuInflater().inflate(R.menu.menu_main, menu);
            if (mDevice != null) {
                switch (mConnectionState) {
                    case CONNECTED:
                        Log.d(TAG, "Create menu in Connected mode");
                        menu.findItem(R.id.menu_connect).setVisible(false);
                        menu.findItem(R.id.menu_disconnect).setVisible(true);
                        menu.findItem(R.id.menu_refresh).setVisible(true);
                        break;
                    case CONNECTING:
                        Log.d(TAG, "Create menu in Connecting mode");
                        menu.findItem(R.id.menu_connect).setVisible(false);
                        menu.findItem(R.id.menu_disconnect).setVisible(false);
                        menu.findItem(R.id.menu_refresh).setVisible(false);
                        break;
                    case DISCONNECTING:
                        Log.d(TAG, "Create menu in Disconnecting mode");
                        menu.findItem(R.id.menu_connect).setVisible(false);
                        menu.findItem(R.id.menu_disconnect).setVisible(false);
                        menu.findItem(R.id.menu_refresh).setVisible(false);
                        break;
                    case DISCONNECTED:
                        Log.d(TAG, "Create menu in Disconnected mode");
                        menu.findItem(R.id.menu_connect).setVisible(true);
                        menu.findItem(R.id.menu_disconnect).setVisible(false);
                        menu.findItem(R.id.menu_refresh).setVisible(false);
                        break;
                    case BLE_EXCHANGE_READ:
                        Log.d(TAG, "Create menu in Exchange Read mode");
                        menu.findItem(R.id.menu_connect).setVisible(false);
                        menu.findItem(R.id.menu_disconnect).setVisible(false);
                        menu.findItem(R.id.menu_refresh).setVisible(false);
                        break;
                    case BLE_EXCHANGE_WRITE:
                        Log.d(TAG, "Create menu in Exchange Write mode");
                        menu.findItem(R.id.menu_connect).setVisible(false);
                        menu.findItem(R.id.menu_disconnect).setVisible(false);
                        menu.findItem(R.id.menu_refresh).setVisible(false);
                        break;
                }

                if (dbDevice.isFavourite()) {
                    menu.findItem(R.id.menu_favourite).setIcon(R.drawable.ic_star_black_24dp);
                } else {
                    menu.findItem(R.id.menu_favourite).setIcon(R.drawable.ic_star_border_black_24dp);
                }
                menu.findItem(R.id.menu_favourite).setVisible(true);
            } else {
                Log.d(TAG, "Create menu in Detached mode");
                menu.findItem(R.id.menu_connect).setVisible(false);
                menu.findItem(R.id.menu_disconnect).setVisible(false);
                menu.findItem(R.id.menu_refresh).setVisible(false);
                menu.findItem(R.id.menu_favourite).setVisible(false);
            }

            return true;
        }

        private void updateStatus(int resId) {
            tvStatus.setText(resId);
        }

        private void updateStatus() {
            switch (mConnectionState) {
                case CONNECTING:
                    tvStatus.setText(R.string.status_connecting);
                    break;
                case CONNECTED:
                    tvStatus.setText(R.string.status_connected);
                    break;
                case DISCONNECTING:
                    tvStatus.setText(R.string.status_disconnecting);
                    break;
                case DISCONNECTED:
                    tvStatus.setText(R.string.status_disconnected);
                    break;
                case BLE_EXCHANGE_READ:
                    tvStatus.setText(R.string.status_loading);
                    break;
                case BLE_EXCHANGE_WRITE:
                    tvStatus.setText(R.string.status_sending);
                    break;
            }
        }
    }

    private class VisitorEditActivity {

        private static final String TAG = "VisitorEdit";

        private ArrayList<Device> reidDevices;
        private Visitor currentVisitor;
        private HashMap<String, Visitor.Access> currentAccess;

        private LinearLayout mainLayout;
        private ListView accessListView;

        class AccessListAdapter extends BaseAdapter {
            Context context;
            LayoutInflater inflater;

            ArrayList<Device> devices;
            Map<String, Visitor.Access> access;

            private AccessListAdapter(Context context, ArrayList<Device> devices, Map<String, Visitor.Access> access) {
                this.context = context;
                this.devices = devices;
                this.access = access;
                inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public int getCount() {
                if (devices == null) {
                    return 0;
                } else {
                    return devices.size();
                }
            }

            @Override
            public Object getItem(int position) {
                if (devices == null) {
                    return null;
                }

                return devices.get(position);
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = inflater.inflate(R.layout.listitem_access, parent, false);
                }

                Device device = (Device)getItem(position);

                TextView nameTextView = view.findViewById(R.id.device_name);
                String label = device.getName();
                if (TextUtils.isEmpty(label)) {
                    label = device.getAddress();
                }
                nameTextView.setText(label);

                CheckBox accessIndicator = view.findViewById(R.id.devSelected);
                Visitor.Access a = access.get(device.getAddress());
                accessIndicator.setChecked(a != null && a.granted);

                return view;
            }
        }

        void onCreate() {
            reidDevices = new ArrayList<>();
            currentAccess = new HashMap<>();

            mainLayout = findViewById(R.id.editMain);

            AccessListAdapter accessAdapter = new AccessListAdapter(MainActivity.this, reidDevices, currentAccess);
            accessListView = mainLayout.findViewById(R.id.deviceList);
            accessListView.setAdapter(accessAdapter);

            accessListView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
                CheckBox checkBox = view.findViewById(R.id.devSelected);
                boolean isChecked = ! checkBox.isChecked();
                checkBox.setChecked(isChecked);
                AccessListAdapter a = (AccessListAdapter)accessListView.getAdapter();
                Device device = (Device)a.getItem(position);
                Visitor.Access access = currentAccess.get(device.getAddress());
                if (access == null) {
                    access = new Visitor.Access();
                    access.oldGranted = false;
                }
                access.granted = isChecked;
                currentAccess.put(device.getAddress(), access);
            });

        }

        void onStart() {
            ArrayList<Device> dbDevices = db.getAllDevices();
            if (mDevice != null) {
                for (int i = 0; i < dbDevices.size(); i++) {
                    Device d = dbDevices.get(i);
                    if (d.getAddress().equals(mDevice.getAddress())) {
                        d.setName(mDevice.getName());
                        break;
                    }
                }
            }
            reidDevices.addAll(dbDevices);
        }

        void onResume() {
            currentVisitor = visitors.get(currentVisitorIdx);

            ImageView personPreview = mainLayout.findViewById(R.id.person_photo);
            Bitmap photoPreview = currentVisitor.getPhoto();
            if (photoPreview != null) {
                personPreview.setImageBitmap(photoPreview);
            } else {
                personPreview.setImageResource(R.drawable.ic_unknown_person);
            }

            /*ImageView accessIndicator = editMain.findViewById(R.id.person_indicator);
            if (peoplePermitted.contains(currentUserToWrite.getId())) {
                accessIndicator.setVisibility(View.VISIBLE);
            } else {
                accessIndicator.setVisibility(View.INVISIBLE);
            }*/

            currentAccess.clear();
            HashMap<String, Visitor.Access> a = currentVisitor.getAccesses();
            if (a != null) {
                currentAccess.putAll(a);
            }

            ((BaseAdapter)accessListView.getAdapter()).notifyDataSetChanged(); // ?

            EditText personName = mainLayout.findViewById(R.id.visitorName);
            personName.setText(currentVisitor.getName());
            personName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

            EditText personDescription = mainLayout.findViewById(R.id.visitorDescription);
            personDescription.setText(currentVisitor.getDescription());
        }

        void onPause() { }

        void onStop() { }

        void onBackPressed() {
            Log.v(TAG, "onBackPressed()");
            editMode = false;
            switchViews();
        }

        boolean onOptionsItemSelected(MenuItem item) {
            Log.v(TAG, "onOptionsItemSelected(): " + item.getItemId());
            switch (item.getItemId()) {
                case R.id.menu_save_person:
                    EditText personName = mainLayout.findViewById(R.id.visitorName);
                    String newName = personName.getText().toString();
                    if (newName.isEmpty()) {
                        personName.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_warning_black_24dp, 0);
                        return true;
                    } else {
                        personName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    }
                    currentVisitor.setName(newName);

                    EditText personDescription = mainLayout.findViewById(R.id.visitorDescription);
                    String newDescription = personDescription.getText().toString();
                    currentVisitor.setDescription(newDescription);
                    currentVisitor.setAccesses(currentAccess);

                    db.updateVisitor(currentVisitor);
                    for (String addr : currentAccess.keySet()) {
                        Visitor.Access access = currentAccess.get(addr);
                        if (access != null && (access.granted || access.oldGranted)) {
                            db.createOrUpdateAccess(currentVisitor.getId(), addr, currentAccess.get(addr));
                        } else {
                            db.deleteAccess(currentVisitor.getId(), addr);
                        }
                    }

                    if (mDevice != null && mConnectionState != ConnectionState.DISCONNECTED) {
                        Visitor.Access access = currentAccess.get(mDevice.getAddress());
                        if (access != null && access.granted) {
                            Log.d(TAG, "Sending request to add new person");
                            mGatt.sendBleAddVisitor(currentVisitor);
                            mConnectionState = ConnectionState.BLE_EXCHANGE_WRITE;
                            invalidateOptionsMenu();
                            visitorListActivity.updateStatus();
                            visitorListActivity.rlProgress.setVisibility(View.VISIBLE);
                        } else {
                            Log.d(TAG, "Sending request to drop a person");
                            mGatt.sendBleDropVisitor(currentVisitor);
                            mConnectionState = ConnectionState.BLE_EXCHANGE_WRITE;
                            invalidateOptionsMenu();
                            visitorListActivity.updateStatus();
                        }
                    }
                    visitorListActivity.peopleAdapter.notifyDataSetChanged();
                    return true;
                case R.id.menu_load_person:
                    // TODO
                    return true;
                case R.id.menu_drop_person:
                    visitors.remove(currentVisitorIdx);
                    db.deleteVisitor(currentVisitor);

                    if (mDevice != null && mConnectionState != ConnectionState.DISCONNECTED) {
                        Log.d(TAG, "Sending request to drop a person");
                        mGatt.sendBleDropVisitor(currentVisitor);
                        mConnectionState = ConnectionState.BLE_EXCHANGE_WRITE;
                        invalidateOptionsMenu();
                        visitorListActivity.updateStatus();
                    }

                    visitorListActivity.peopleAdapter.notifyDataSetChanged();

                    currentVisitorIdx = -1;
                    currentVisitor = null;
                    onBackPressed();
                    return true;
                case android.R.id.home:
                    onBackPressed();
                    return true;
            }
            return false;
        }

        boolean onCreateOptionsMenu(Menu menu) {
            getMenuInflater().inflate(R.menu.menu_person_edit, menu);

            if (mDevice != null) {
                if ((mConnectionState == ConnectionState.CONNECTING) ||
                    (mConnectionState == ConnectionState.BLE_EXCHANGE_READ) ||
                    (mConnectionState == ConnectionState.BLE_EXCHANGE_WRITE)) {
                    menu.findItem(R.id.menu_save_person).setVisible(false);
                    menu.findItem(R.id.menu_drop_person).setVisible(false);
                    //menu.findItem(R.id.menu_load_person).setVisible(false);
                } else {
                    menu.findItem(R.id.menu_save_person).setVisible(true);
                    menu.findItem(R.id.menu_drop_person).setVisible(true);
                    //menu.findItem(R.id.menu_load_person).setVisible(true);
                }

            } else {
                menu.findItem(R.id.menu_save_person).setVisible(true);
                menu.findItem(R.id.menu_drop_person).setVisible(true);
                //menu.findItem(R.id.menu_load_person).setVisible(false);
            }

            return true;
        }
    }

    private class BroadcastReceiver implements BluetoothLeService.Receiver {

        private BluetoothGattCharacteristic characteristicFifo;

        private Visitor currentUserToWrite = null;
        private Visitor currentUserToRead = new Visitor();
        private byte[] currentUserPhotoToRead = new byte[128*128];
        private int currentUserPhotoRead = 0;
        private byte[] currentUserDescriptorToRead = new byte[512*2];
        private int currentUserDescriptorRead = 0;

        private byte currentBleRequest;

        private static final byte BLE_ACK = 0x33;

        private static final byte BLE_ID_REQUEST = 0x0F;

        private static final byte BLE_READ_STRANGER = 0x10;
        private static final byte BLE_GET_STRANGER_NAME = 0x11;
        private static final byte BLE_GET_STRANGER_PHOTO = 0x12;
        private static final byte BLE_GET_STRANGER_DESCRIPTOR = 0x13;
        private static final byte BLE_DROP_STRANGER = 0x14; // TODO: to be dropped

        private static final byte BLE_READ_VISITOR = 0x15;
        private static final byte BLE_GET_VISITOR_NAME = 0x16;
        private static final byte BLE_GET_VISITOR_DESCRIPTOR = 0x17;
        private static final byte BLE_DROP_VISITOR = 0x18;

        private static final byte BLE_WRITE = 0x20;
        private static final byte BLE_SET_NAME = 0x21;
        private static final byte BLE_SET_DESCRIPTOR = 0x22;

        private static final byte BLE_EXIT = 0x55;

        private static final byte BLE_HEART_BEAT = 0x56;

        private static final int hbInterval = 10000; // 10s
        private Timer hbTimer = null;

        private final byte[] ReID = { 0x52, 0x65, 0x49, 0x44 };

        private static final int connectionTimeout = 10000; // 10s
        private Timer connectionTimer = null;
        private Timer backTimer = null;

        private void onConnectionFailure() {
            // Show error message for 2 seconds and go back to device activity
            runOnUiThread(() -> {
                visitorListActivity.updateStatus(R.string.status_connection_failed);
                visitorListActivity.rlProgress.setVisibility(View.GONE);
            });
            backTimer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(() -> {
                        editMode = false;
                        onBackPressed();
                    });
                }
            };
            backTimer.schedule(timerTask, 2000);
        }

        private void startConnectionTimer() {
            connectionTimer = new Timer();
            TimerTask connectionTask = new TimerTask() {
                @Override
                public void run() {
                    Log.w(TAG, "No response received from connected device on ID request");
                    onConnectionFailure();
                }
            };
            connectionTimer.schedule(connectionTask, connectionTimeout);
        }

        private void stopConnectionTimer() {
            if (connectionTimer != null) {
                connectionTimer.cancel();
                connectionTimer = null;
            }
            if (backTimer != null) {
                backTimer.cancel();
                backTimer = null;
            }
        }

        private void startHBTimer() {
            hbTimer = new Timer();
            TimerTask hbTask = new TimerTask() {
                @Override
                public void run() {
                    mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_HEART_BEAT});
                }
            };
            hbTimer.schedule(hbTask, hbInterval / 2, hbInterval);
        }

        private void stopHBTimer() {
            if (hbTimer != null) {
                hbTimer.cancel();
                hbTimer = null;
            }
        }

        private void sendBleIdentificationRequest() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_ID_REQUEST});
            currentBleRequest = BLE_ID_REQUEST;
        }

        private void sendBleReadStranger() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_READ_STRANGER});
            currentBleRequest = BLE_READ_STRANGER;
        }

        private void sendBleGetStrangerName() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_GET_STRANGER_NAME});
            currentBleRequest = BLE_GET_STRANGER_NAME;
        }

        private void sendBleGetStrangerPhoto() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_GET_STRANGER_PHOTO});
            currentBleRequest = BLE_GET_STRANGER_PHOTO;
        }

        private void sendBleGetStrangerDescriptor() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_GET_STRANGER_DESCRIPTOR});
            currentBleRequest = BLE_GET_STRANGER_DESCRIPTOR;
        }

        private void sendBleDropStranger() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_DROP_STRANGER});
            currentBleRequest = BLE_DROP_STRANGER;
        }

        private void sendBleReadVisitor() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_READ_VISITOR});
            currentBleRequest = BLE_READ_VISITOR;
        }

        private void sendBleGetVisitorName() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_GET_VISITOR_NAME});
            currentBleRequest = BLE_GET_VISITOR_NAME;
        }

        private void sendBleGetVisitorDescriptor() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_GET_VISITOR_DESCRIPTOR});
            currentBleRequest = BLE_GET_VISITOR_DESCRIPTOR;
        }

        private void sendBleDropVisitor(Visitor visitor) {
            stopHBTimer();
            currentUserToWrite = visitor;
            byte[] descriptor = visitor.getDescriptor();
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_DROP_VISITOR});
            int chunkSize = 20;
            int packetsToSend = (descriptor.length + chunkSize - 1) / chunkSize;
            for (int i = 0; i < packetsToSend; i++) {
                byte[] tmp = Arrays.copyOfRange(descriptor, i * chunkSize, min(i * chunkSize + chunkSize, 1024));
                mBleService.writeCharacteristic(characteristicFifo, tmp);
            }
            currentBleRequest = BLE_DROP_VISITOR;
            startHBTimer();
        }

        private void sendBleAddVisitor(Visitor visitor) {
            currentUserToWrite = visitor;
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_WRITE});
            currentBleRequest = BLE_WRITE;
        }

        private void sendBleSetVisitorName(String name) {
            stopHBTimer();
            byte[] nameBytes = name.getBytes();
            byte[] request = new byte[17];
            request[0] = BLE_SET_NAME;
            for (int i = 0; i < 16; i++) {
                if (i < nameBytes.length) {
                    request[i + 1] = nameBytes[i];
                } else {
                    request[i + 1] = 0x00;
                }
            }
            mBleService.writeCharacteristic(characteristicFifo, request);
            currentBleRequest = BLE_SET_NAME;
            startHBTimer();
        }

        private void sendBleSetVisitorDescriptor(byte[] descriptor) {
            stopHBTimer();
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_SET_DESCRIPTOR});
            int chunkSize = 20;
            int packetsToSend = (descriptor.length + chunkSize - 1) / chunkSize;
            for(int i = 0; i < packetsToSend; i++) {
                byte[] tmp = Arrays.copyOfRange(descriptor, i * chunkSize, min(i * chunkSize + chunkSize, 1024));
                mBleService.writeCharacteristic(characteristicFifo, tmp);
            }
            currentBleRequest = BLE_SET_DESCRIPTOR;
            startHBTimer();
        }

        private void sendBleExit() {
            mBleService.writeCharacteristic(characteristicFifo, new byte[]{BLE_EXIT});
            currentBleRequest = BLE_EXIT;
        }

        @Override
        public void onDescriptorWrite() {
            Log.d(TAG, "onDescriptorWrite call");
        }

        @Override
        public void onPhyAvailable(boolean isUpdate) {
            Log.d(TAG, "onPhyAvailable call");
        }

        @Override
        public void onMtuUpdate(int mtu, int status) {
            Log.d(TAG, "onMtuUpdate call");
        }

        @Override
        public void onRssiUpdate(int rssi) {
            Log.d(TAG, "onRssiUpdate call " + rssi);
        }

        @Override
        public void onDataAvailable(UUID uUid, int type, byte[] data) {
            String typeString;
            switch (type) {
                case ITEM_TYPE_READ:
                    typeString = "ITEM_TYPE_READ";
                break;
                case ITEM_TYPE_WRITE:
                    typeString = "ITEM_TYPE_WRITE";
                    break;
                case ITEM_TYPE_NOTIFICATION:
                    typeString = "ITEM_TYPE_NOTIFICATION";
                    break;
                default:
                    typeString = "UNKNOWN";
            }
            Log.d(TAG, "onDataAvailable call: Data type " + typeString + " with size " + data.length + " is available!");

            if(type == ITEM_TYPE_NOTIFICATION) {
                Log.d(TAG, "BLE request was " + currentBleRequest);

                switch (currentBleRequest) {
                    case BLE_ID_REQUEST:
                        Log.d(TAG, "currentBleRequest == BLE_ID_REQUEST");
                        stopConnectionTimer();
                        if (data.length == ReID.length && Arrays.equals(data, ReID)) {
                            Log.i(TAG, "Valid ReID device found");
                            mConnectionState = ConnectionState.CONNECTED;
                            visitorsPermitted.clear();
                            runOnUiThread(() -> {
                                invalidateOptionsMenu();
                                visitorListActivity.updateStatus();
                                visitorListActivity.rlProgress.setVisibility(View.GONE);
                            });
                            db.createOrUpdateDevice(dbDevice);
                            startHBTimer();
                        } else {
                            Log.w(TAG, "Connected device is not a ReID device");
                            onConnectionFailure();
                        }
                        break;
                    case BLE_READ_STRANGER:
                        Log.d(TAG, "currentBleRequest == BLE_READ_STRANGER");
                        Log.d(TAG, "Response code: " + data[0]);
                        if (data[0] == BLE_ACK) {
                            Log.d(TAG, "Sending stranger name request");
                            sendBleGetStrangerName();
                        } else if (data[0] == 0) {
                            mConnectionState = ConnectionState.CONNECTED;
                            runOnUiThread(() -> {
                                invalidateOptionsMenu();
                                visitorListActivity.updateStatus();
                                visitorListActivity.rlProgress.setVisibility(View.GONE);
                            });
                        }
                        break;
                    case BLE_GET_STRANGER_NAME: {
                        Log.d(TAG, "currentBleRequest == BLE_GET_STRANGER_NAME");
                        String name = CString2String(data);
                        Log.d(TAG, "Name " + name + " got, sending BLE_GET_STRANGER_PHOTO request");
                        currentUserToRead.setName(name);
                        currentUserPhotoRead = 0;
                        sendBleGetStrangerPhoto();
                        break;
                    }
                    case BLE_GET_STRANGER_PHOTO:
                        Log.d(TAG, "currentBleRequest == BLE_GET_STRANGER_PHOTO");
                        System.arraycopy(data, 0, currentUserPhotoToRead, currentUserPhotoRead, data.length);
                        currentUserPhotoRead += data.length;
                        Log.d(TAG, "Received " + currentUserPhotoRead + " bytes from " + currentUserPhotoToRead.length);

                        if (currentUserPhotoRead < currentUserPhotoToRead.length) {
                            // data is sent in chunks by 1024 bytes. New request is needed to get the next portion
                            if(currentUserPhotoRead % 1024 == 0) {
                                Log.d(TAG, "Requesting new chunk of data");
                                sendBleGetStrangerPhoto();
                            }
                        } else {
                            currentUserToRead.setPhotoData(currentUserPhotoToRead.clone());
                            currentUserPhotoRead = 0;
                            Log.d(TAG, "Photo got, sending BLE_GET_STRANGER_DESCRIPTOR request");
                            sendBleGetStrangerDescriptor();
                        }
                        break;
                    case BLE_GET_STRANGER_DESCRIPTOR:
                        Log.d(TAG, "currentBleRequest == BLE_GET_STRANGER_DESCRIPTOR");
                        System.arraycopy(data, 0, currentUserDescriptorToRead, currentUserDescriptorRead, data.length);
                        currentUserDescriptorRead += data.length;
                        Log.d(TAG, "Received " + currentUserDescriptorRead + " bytes from " + currentUserDescriptorToRead.length);
                        if (currentUserDescriptorRead >= currentUserDescriptorToRead.length) {
                            currentUserToRead.setDescriptor(currentUserDescriptorToRead.clone());
                            currentUserDescriptorRead = 0;
                            Log.d(TAG, "Descriptor got, sending BLE_DROP_STRANGER request");
                            sendBleDropStranger();
                        }
                        break;
                    case BLE_DROP_STRANGER:
                        Log.d(TAG, "currentBleRequest == BLE_DROP_STRANGER");
                        if (db.createVisitor(currentUserToRead) >= 0) {
                            visitors.add(currentUserToRead);
                        }

                        currentUserToRead = new Visitor();
                        sendBleReadStranger();
                        runOnUiThread(() -> visitorListActivity.peopleAdapter.notifyDataSetChanged());
                        break;
                    case BLE_READ_VISITOR:
                        Log.d(TAG, "currentBleRequest == BLE_READ_VISITOR");
                        Log.d(TAG, "Response code: " + data[0]);
                        if (data[0] == BLE_ACK) {
                            Log.d(TAG, "Sending visitor name request");
                            sendBleGetVisitorName();
                        } else if (data[0] == 0) {
                            // All visitors are loaded, continue with strangers
                            Log.d(TAG, "Sending read stranger request");
                            sendBleReadStranger();
                        }
                        break;
                    case BLE_GET_VISITOR_NAME: {
                        Log.d(TAG, "currentBleRequest == BLE_GET_VISITOR_NAME");
                        String name = CString2String(data);
                        Log.d(TAG, "Name " + name + " got, sending BLE_GET_VISITOR_DESCRIPTOR request");
                        currentUserToRead.setName(name);
                        currentUserToRead.setOldName(name);
                        sendBleGetVisitorDescriptor();
                        break;
                    }
                    case BLE_GET_VISITOR_DESCRIPTOR:
                        Log.d(TAG, "currentBleRequest == BLE_GET_VISITOR_DESCRIPTOR");
                        System.arraycopy(data, 0, currentUserDescriptorToRead, currentUserDescriptorRead, data.length);
                        currentUserDescriptorRead += data.length;
                        Log.d(TAG, "Received " + currentUserDescriptorRead + " bytes from " + currentUserDescriptorToRead.length);
                        if (currentUserDescriptorRead >= currentUserDescriptorToRead.length) {
                            currentUserToRead.setDescriptor(currentUserDescriptorToRead.clone());
                            currentUserDescriptorRead = 0;
                            Log.d(TAG, "Descriptor got, adding a visitor");

                            // Try to find visitor in DB visitors
                            int i;
                            for (i = 0; i < visitors.size(); i++) {
                                Visitor v = visitors.get(i);
                                if (Arrays.equals(currentUserToRead.getDescriptor(), v.getDescriptor())) {
                                    Visitor.Access access = v.getAccess(mDevice.getAddress());
                                    if (access == null) {
                                        access = new Visitor.Access();
                                        access.granted = true;
                                    }
                                    access.oldGranted = true;
                                    visitorsPermitted.add(v.getId());
                                    v.setAccess(mDevice.getAddress(), access);
                                    db.createOrUpdateAccess(v.getId(), mDevice.getAddress(), access);
                                    v.setOldName(currentUserToRead.getOldName());
                                    db.updateVisitor(v);
                                    break;
                                }
                            }

                            if (i >= visitors.size()) { // visitor is not in DB
                                Visitor.Access access = new Visitor.Access();
                                access.granted = true;
                                access.oldGranted = true;
                                currentUserToRead.setAccess(mDevice.getAddress(), access);
                                db.createVisitor(currentUserToRead);
                                db.createAccess(currentUserToRead.getId(), mDevice.getAddress(), access);
                                visitors.add(currentUserToRead);
                                visitorsPermitted.add(currentUserToRead.getId());
                            }

                            currentUserToRead = new Visitor();
                            sendBleReadVisitor();
                            runOnUiThread(() -> visitorListActivity.peopleAdapter.notifyDataSetChanged());
                        }
                        break;
                    case BLE_DROP_VISITOR:
                        Log.d(TAG, "currentBleRequest == BLE_DROP_VISITOR");
                        Log.d(TAG, "Response code: " + data[0]);
                        if (data[0] == BLE_ACK) {
                            Visitor.Access access = currentUserToWrite.getAccess(mDevice.getAddress());
                            if (access != null) {
                                access.oldGranted = false;
                                currentUserToWrite.setAccess(mDevice.getAddress(), access);
                                db.createOrUpdateAccess(currentUserToWrite.getId(), mDevice.getAddress(), access);
                            } else {
                                db.deleteAccess(currentUserToWrite.getId(), mDevice.getAddress());
                            }
                            visitorsPermitted.remove(Integer.valueOf(currentUserToWrite.getId()));
                            currentUserToWrite.setOldName(null);
                            db.updateVisitor(currentUserToWrite);
                        } else if (data[0] == 0) {
                            Log.d(TAG, "Failed to drop visitor: No such visitor");
                        }
                        mConnectionState = ConnectionState.CONNECTED;
                        runOnUiThread(() -> {
                            invalidateOptionsMenu();
                            visitorListActivity.updateStatus();
                            visitorListActivity.rlProgress.setVisibility(View.GONE);
                            if (data[0] == BLE_ACK) {
                                visitorListActivity.peopleAdapter.notifyDataSetChanged();
                            }
                        });
                        break;
                    case BLE_WRITE:
                        Log.d(TAG, "currentBleRequest == BLE_WRITE");
                        Log.d(TAG, "Response code: " + data[0]);
                        if (data[0] == BLE_ACK)
                        {
                            Log.d(TAG, "Sending request to set name");
                            sendBleSetVisitorName(currentUserToWrite.getName());
                        } else {
                            Log.d(TAG, "Device responded with non BLE_ACK code: " + data[0]);
                        }
                        break;
                    case BLE_SET_NAME:
                        Log.d(TAG, "currentBleRequest == SET_NAME");
                        Log.d(TAG, "Response code: " + data[0]);
                        if (data[0] == BLE_ACK) {
                            Log.d(TAG, "Sending request to set descriptor");
                            sendBleSetVisitorDescriptor(currentUserToWrite.getDescriptor());
                        }
                        break;
                    case BLE_SET_DESCRIPTOR:
                        Log.d(TAG, "currentBleRequest == SET_DESCRIPTOR");
                        Log.d(TAG, "Response code: " + data[0]);
                        if (data[0] == BLE_ACK) {
                            Visitor.Access access = currentUserToWrite.getAccess(mDevice.getAddress());
                            access.oldGranted = true;
                            currentUserToWrite.setAccess(mDevice.getAddress(), access);
                            visitorsPermitted.add(currentUserToWrite.getId());
                            db.createOrUpdateAccess(currentUserToWrite.getId(), mDevice.getAddress(), access);
                            currentUserToWrite.setOldName(currentUserToWrite.getName());
                            db.updateVisitor(currentUserToWrite);
                            currentUserToWrite = null;
                        } else if (data[0] == 0) {
                            Log.d(TAG, "Failed to add visitor: DB is full");
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.maximum_visitors_toast, Toast.LENGTH_SHORT).show());
                        }
                        mConnectionState = ConnectionState.CONNECTED;
                        runOnUiThread(() -> {
                            invalidateOptionsMenu();
                            visitorListActivity.updateStatus();
                            visitorListActivity.rlProgress.setVisibility(View.GONE);
                            if (data[0] == BLE_ACK) {
                                visitorListActivity.peopleAdapter.notifyDataSetChanged();
                            }
                        });
                        break;
                    case BLE_EXIT:
                        Log.d(TAG, "currentBleRequest == EXIT");
                        Log.d(TAG, "Response code: " + data[0]);
                        if (data[0] == BLE_ACK) {
                            mBleService.disconnect();
                        }
                        break;
                }
            }
        }

        @Override
        public void onServicesDiscovered() {
            Log.d(TAG, "onServicesDiscovered call");
            runOnUiThread(() -> {
                visitorListActivity.updateStatus();
                for (BluetoothGattService service : mBleService.getSupportedGattServices()) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        String uuid = characteristic.getUuid().toString();
                        if (uuid.equals(GattAttributes.UUID_CHARACTERISTIC_FIFO)) {
                            Log.d(TAG,"Found FIFO characteristic!\n");
                            characteristicFifo = characteristic;
                            mBleService.setCharacteristicNotification(characteristic, true);
                            // Ready to send and receive messages
                            sendBleIdentificationRequest();
                            startConnectionTimer();
                        } else if (uuid.equals(GattAttributes.UUID_CHARACTERISTIC_CREDITS)) {
                            Log.d(TAG,"Found Credits characteristic!\n");
                            mBleService.setCharacteristicNotification(characteristic, false);
                            visitorListActivity.updateStatus();
                        }
                    }
                }
            });
        }

        @Override
        public void onGattDisconnected() {
            runOnUiThread(() -> {
                mConnectionState = ConnectionState.DISCONNECTED;
                invalidateOptionsMenu();
                visitorListActivity.updateStatus();
                visitorListActivity.rlProgress.setVisibility(View.GONE);
            });
            stopHBTimer();
        }

        @Override
        public void onGattConnected() {
            Log.d(TAG, "onGattConnected call");
        }

        private String CString2String(byte[] data) {
            int i;
            for (i = 0; i < data.length; i++) {
                if (data[i] == 0x00) {
                    break;
                }
            }
            return new String(data, 0, i);
        }
    }
}
