package testclient;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import models.Device;
import models.Household;
import models.User;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

// Limited test client

public class TestClient {
    private static final int OPCODE_STATUS_DEVICE = 10;
    private static final int OPCODE_STATUS_ALL_DEVICES = 11;
    private static final int OPCODE_CHANGE_DEVICE_STATUS = 20;
    private static final int OPCODE_CHANGE_DEVICE_HOUSEHOLD = 21;


    private static final int OPCODE_CREATE_USER = 23;
    private static final int OPCODE_CREATE_USER_OK = 13;

    private static final int OPCODE_NEW_TOKEN = 12;
    private static final int OPCODE_REQUEST_TOKEN = 22;
    private static final int OPCODE_NEW_TOKEN_NOTOK = 42;

    private static final int OPCODE_CREATE_HOUSEHOLD_OK = 14;
    private static final int OPCODE_CREATE_HOUSEHOLD = 24;



    private static WebSocket webSocket;
    private static String SERVER_PATH_STATIC_TOKEN = "ws://localhost:7071/house?token=123";
//    private static String SERVER_PATH = "ws://localhost:7071/house?token=";
    private static String AUTH_SERVER_PATH = "ws://85.197.159.32:7071/houseauth";
    private static String SERVER_PATH = "ws://85.197.159.150:1337/house?token=123";
    //  85.197.159.150

//    private static String SERVER_PATH = "ws://192.168.0.106:7071/house?token=123";
//    private static String SERVER_PATH = "ws://85.197.159.131:1337/house?token=123";
    private static Gson gson;
    private static Scanner scanner;

    private static List<Device> deviceList;
    private static User user;
    private static Household household;

    private static String token = "";

    //Test
    private static long okhttpThreadId;

    public static void main(String[] args) throws InterruptedException {
        scanner = new Scanner(System.in);
        gson = new Gson();

        //Initiade connection
//        connectWebsocket();

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "Jjj");
        jsonObject.put("message", "hello");

        household = new Household();
        household.setName("Hemma hos Torsten");
        user = new User();
        user.setName("Torsten Gnusås");
        user.setPassword("123");
        user.setUsername("user@mail.com");
        user.setHouseholdId(0);
        deviceList = new ArrayList<>();

        String input;
        while (true) {
            System.out.println("Main Thread: " + Thread.currentThread().getId() + " " + Thread.currentThread().getName());
            System.out.println();
            System.out.println("Enter command: ");
            input = scanner.next();

            switch (input) {
                case "connect":
                    connectWebsocket();
                    break;
                case "auth":
                    connectauthWebsocket();
                    break;
                case "createuser":
                    createUser();
                    break;
                case "household":
                    createHousehold();
                    break;
                case "getDevice":
                    getDevice();
                    break;
                case "getAllDevices":
                    break;
                case "change":
                    changeDevice();
                    break;
                case "change2":
                    changeDevice2();
                    break;
                case "token":
                    getToken();
                    break;
                case "test":
                    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
                    System.out.println("----threadset size: " + threadSet.size());
                    for (Thread t: threadSet) {
                        System.out.println(t.getId() + " - " + t.getName());
                        if (t.getId() == okhttpThreadId) {
                            System.out.println(okhttpThreadId + " joining " + t.getId());
                            t.join();
                        }
                    }
                    System.out.println("---------------------");
                    break;
                case "kill":
                    break;
                case "quit":
                    webSocket.close(1000, "hejdå");
                    System.exit(0);
            }
        }
    }

    private static void connectWebsocket() {
        OkHttpClient client = new OkHttpClient();
        System.out.println("connect: " + SERVER_PATH + token);
        Request request = new Request.Builder().url(SERVER_PATH + token).build();
        webSocket = client.newWebSocket(request, new SocketListener());
    }

    private static void connectauthWebsocket() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(AUTH_SERVER_PATH).build();
        webSocket = client.newWebSocket(request, new SocketListenerAuth());
    }

    // --- Websocket House auth methods
    private static void getToken() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", OPCODE_REQUEST_TOKEN);
        jsonObject.put("data", gson.toJson(user));
        System.out.println("Sending: " + jsonObject);
        webSocket.send(jsonObject.toString());
    }

    private static void createUser() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", OPCODE_CREATE_USER);
        jsonObject.put("data", gson.toJson(user));
        System.out.println("Sending: " + jsonObject);
        webSocket.send(jsonObject.toString());
    }

    private static void createHousehold() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", OPCODE_CREATE_HOUSEHOLD);
        jsonObject.put("data", gson.toJson(household));
        System.out.println("Sending: " + jsonObject);
        webSocket.send(jsonObject.toString());
    }


    // --- Websocket House methods
    private static void getDevice() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", 10);
        System.out.println("Sending: " + jsonObject);
        webSocket.send(jsonObject.toString());
    }

    private static void changeDevice() {
        Device device = new Device(1, "kitchen lamp", "lamp", 0, 0);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", OPCODE_CHANGE_DEVICE_STATUS);
        jsonObject.put("data", gson.toJson(device));
        System.out.println("Sending: " + jsonObject);
        webSocket.send(jsonObject.toString());
    }

    private static void changeDevice2() {
        Device device = new Device(1, "kitchen lamp", "lamp", 1, 0);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", OPCODE_CHANGE_DEVICE_STATUS);
        jsonObject.put("data", gson.toJson(device));
        System.out.println("Sending: " + jsonObject);
        webSocket.send(jsonObject.toString());
    }

    //SocketListener House
    private static class SocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);

            System.out.println("Socket Connection successful");
            System.out.println("onOpen Thread id: " + Thread.currentThread().getId());
            System.out.println("onOpen Thread id: " + Thread.currentThread().getName());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            System.out.println("onMessage Method");
            System.out.println(webSocket.request());



            JSONObject jsonObject = new JSONObject(text);
            System.out.println(jsonObject.toString());
            int opcode = jsonObject.getInt("opcode");
            if (opcode != OPCODE_STATUS_ALL_DEVICES) {
                return;
            }

            JSONArray jsonArray = (JSONArray) jsonObject.get("data");
            for (int i = 0; i < jsonArray.length(); i++) {
                deviceList.add(gson.fromJson(jsonArray.get(i).toString(), Device.class));
                System.out.println(deviceList.get(i).getName());
            }

            System.out.println("onMessage END");
        }

//        @Override
//        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
//            super.onClosed(webSocket, code, reason);
//            System.out.println(webSocket.request());
//            System.out.println("closed " + code + "|" + reason);
//        }
//
//        @Override
//        public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
//            super.onClosing(webSocket, code, reason);
//            System.out.println(webSocket.request());
//            System.out.println("closing " + code + "|" + reason);
//        }
    }

    //SocketListener authHouse
    private static class SocketListenerAuth extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);

            System.out.println("autHouse: Socket Connection successful");
            System.out.println("autHouse Thread id: " + Thread.currentThread().getId());
            System.out.println("autHouse Thread name: " + Thread.currentThread().getName());
            okhttpThreadId = Thread.currentThread().getId();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            System.out.println("authHouse: onMessage - connected to: " + webSocket.request());

            JSONObject jsonObject = new JSONObject(text);
            int opcode = jsonObject.getInt("opcode");

            switch (opcode) {
                case OPCODE_NEW_TOKEN_NOTOK:
                    System.out.println("authHouse: onMessage - No token created");
                    break;
                case OPCODE_NEW_TOKEN:
                    token = (String) jsonObject.get("data");
                    System.out.println("authHouse: onMessage - New token stored: " + token);
                    break;
                case OPCODE_CREATE_USER_OK:
                    System.out.println("authHouse: onMessage - New user created: " + jsonObject);
                    break;
                default:
                    System.out.println("authHouse: UKNOWN OPCODE: " + opcode);
                    break;
            }
//            webSocket.close(0, "finished");
            System.out.println("---onMessage END---");
        }
    }
}
