package javalintest;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import models.Device;
import models.Household;
import models.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int OPCODE_STATUS_DEVICE = 10;
    private static final int OPCODE_STATUS_ALL_DEVICES = 11;
    private static final int OPCODE_CHANGE_DEVICE_STATUS = 20;
    private static final int OPCODE_CHANGE_DEVICE_INFO = 21;
    private static final int OPCODE_CHANGE_DEVICE_NOTOK = 40;
    private static final int OPCODE_CHANGE_DEVICE_INFO_NOTOK = 41;

    private static final int OPCODE_CREATE_USER = 23;
    private static final int OPCODE_CREATE_USER_OK = 13;
    private static final int OPCODE_CREATE_USER_NOTOK = 43;

    private static final int OPCODE_NEW_TOKEN = 12;
    private static final int OPCODE_REQUEST_TOKEN = 22;
    private static final int OPCODE_NEW_TOKEN_NOTOK = 42;
    private static final int OPCODE_INVALID_TOKEN = 49;
    private static final int OPCODE_FORGOT_PASSWORD = 99;

    private static final int OPCODE_CREATE_HOUSEHOLD_OK = 14;
    private static final int OPCODE_CREATE_HOUSEHOLD = 24;
    private static final int OPCODE_USER_CHANGE_HOUSEHOLD = 25;
    private static final int OPCODE_USER_CHANGE_HOUSEHOLD_NOTOK = 45;


    private static final int CONNECTION_IDLE_TIMEOUT = 3600000;

    private static Map<WsContext, User> userUsernameMap = new ConcurrentHashMap<>();
    private static Map<WsContext, User> authUsernameMap = new ConcurrentHashMap<>();

    private static Gson gson;

    static List<Device> deviceList;
    static List<User> userList;
    static List<Household> householdList;
    static Household household;
    static Device device1, device2, device3, device4, device5, device6,
            device7, device8, device9, device10, device11, device12,
            device13, device14, device15, device16, device17;
    static User demoUser, demoUser2;

    public static void main(String[] args) {
        System.out.println(Thread.currentThread().getId());
        gson = new Gson();
        ///// demo items
        device1 = new Device(1, "Outdoor lighting", "lamp", 0, 1);
        device2 = new Device(2, "Indoor lighting", "lamp", 0, 1);
        device3 = new Device(3, "Heating Element", "element", 0, 1);
        device4 = new Device(4, "Heating Element wind", "element", 0, 1);
        device9 = new Device(9, "Fire Alarm", "alarm", 1, 1);
        device10 = new Device(10, "Inside temperature", "thermometer", 232, 1);
        device11 = new Device(11, "Fan", "fan", 0, 1);
        device12 = new Device(12, "Burglar Alarm", "alarm", 1, 1);
        device13 = new Device(13, "Water Leakage", "alarm", 1, 1);
        device14 = new Device(14, "Automatic outdoor lighting", "autotoggle", 0, 1);
        device15 = new Device(15, "Automatic heating", "autotoggle", 0, 1);
        device16 = new Device(16, "Power consumption", "powersensor", 70, 1);
        device17 = new Device(17, "Desired temp", "autosettings", 200, 1);

        deviceList = new ArrayList<>();
        deviceList.add(device1);
        deviceList.add(device2);
        deviceList.add(device3);
        deviceList.add(device4);
        deviceList.add(device9);
        deviceList.add(device10);
        deviceList.add(device11);
        deviceList.add(device12);
        deviceList.add(device13);
        deviceList.add(device14);
        deviceList.add(device15);
        deviceList.add(device16);
        deviceList.add(device17);

        userList = new ArrayList<>();
        demoUser = new User(1, "user@mail.com", "Valter", 1, "user@mail.com$123");
        demoUser2 = new User(2, "jonte@mail.com", "Jonte", 1, "jonte@mail.com$123");
        demoUser.setPassword("123");
        demoUser2.setPassword("123");
        userList.add(demoUser);
        userList.add(demoUser2);

        householdList = new ArrayList<>();
        household = new Household(1, "SimpizHus");
        householdList.add(household);
        ////

        Javalin app = Javalin.create(config -> {
            config.wsFactoryConfig(wsFactory -> {
                wsFactory.getPolicy().setIdleTimeout(CONNECTION_IDLE_TIMEOUT);
            });
        }).start(7071);

        // ---- House websocket ----
        app.ws("/house", ws -> {
            ws.onConnect(ctx -> {
                String username = "", password = "";
                //Token received from URL (example: ws://localhost:7071/house?token=123)
                String token = ctx.queryParam("token");
                String[] splitToken = token.split("\\$");
                username = splitToken[0];
                System.out.println("House: username: " + username);
                if(splitToken.length > 1) {
                    password = splitToken[1];
                }


                User loginUser = null;
                //TODO: get user from database, store as "user"
                for (User user : userList) {
                    if (user.getUsername().equals(username)) {
                        loginUser = user;
                    }
                }

                if (loginUser == null) {
                    System.out.println("User [" + username + "]not found, closing: " + token);
                    sendInvalidTokenResponse(ctx);
                    ctx.session.close(1000, "Invalid user");
                    return;
                }

                //On new connection, check if valid token
                if (token.equals("") || !password.equals(loginUser.getPassword())) {
                    System.out.println("incorrect token, closing: " + token);
                    //Send "invalid token" and disconnect
                    sendInvalidTokenResponse(ctx);
                    ctx.session.close(1000, "Invalid token");
                    return;
                }

                //Store connection in map
                userUsernameMap.put(ctx, loginUser);
                System.out.println(loginUser.getUsername() + " connected");

                //Send all device status to new connection
                sendAllDevice(ctx);
            });
            ws.onClose(ctx -> {
                User disconnectedUser = userUsernameMap.remove(ctx);

                if (disconnectedUser != null) {
                    System.out.println(disconnectedUser.getUsername() + " disconnected. Reason: (" +
                            ctx.status() + ")" + ctx.reason());
                }
                System.out.println("Current number of connected users: " + userUsernameMap.size());
            });
            ws.onMessage(ctx -> {
                System.out.println("Received message");
                System.out.println(ctx.message());

                //Message arrives as JSON
                JSONObject msgFromClient = new JSONObject(ctx.message());

                //Get opcode to determine message action
                int opcode = msgFromClient.getInt("opcode");
                System.out.println(" - Message Opcode: " + opcode);
                switch (opcode) {
                    case OPCODE_STATUS_ALL_DEVICES:
                        break;

                    case OPCODE_CHANGE_DEVICE_STATUS:
                        System.out.println(" - Change device status");
                        changeDeviceStatus(ctx, msgFromClient);
                        break;
                    case OPCODE_CHANGE_DEVICE_INFO:
                        System.out.println(" - Change device info");
                        changeDeviceInfo(ctx, msgFromClient);
                        break;
                    case OPCODE_USER_CHANGE_HOUSEHOLD:
                        changeUserHousehold(ctx, msgFromClient);
                        break;
                    case OPCODE_CREATE_HOUSEHOLD:
                        createHousehold(ctx, msgFromClient);
                        break;
                }
            });
        });

        // ---- House Auth websocket ----
        app.ws("/houseauth", ws -> {
            ws.onConnect(ctx -> {
                //TODO: get user from database, store as "user"
                //Using demo user for now
                User user = demoUser;

                //Store connection in map
                authUsernameMap.put(ctx, demoUser);
                System.out.println("# Houseauth: new user connected to houseauth");
            });
            ws.onClose(ctx -> {
                User disconnectedUser = authUsernameMap.remove(ctx);
                if (disconnectedUser != null) {
                    System.out.println(disconnectedUser.getUsername() + " disconnected. Reason: (" +
                            ctx.status() + ")" + ctx.reason());
                }
                System.out.println("Houseauth: Current number of connected users: " + authUsernameMap.size());
            });
            ws.onMessage(ctx -> {
                System.out.println("# Houseauth: Received message");
                System.out.println(ctx.message());

                //Message arrives as JSON
                JSONObject msgFromClient = new JSONObject(ctx.message());

                //Get opcode to determine message action
                int opcode = msgFromClient.getInt("opcode");
                System.out.println("Houseauth:  - Message Opcode: " + opcode);
                switch (opcode) {
                    case OPCODE_REQUEST_TOKEN:
                        System.out.println(" - Request Token");
                        requestToken(ctx, msgFromClient);
                        break;
                    case OPCODE_CREATE_USER:
                        System.out.println(" - Create User");
                        createUser(ctx, msgFromClient);
                        break;
                    case OPCODE_CREATE_HOUSEHOLD:
                        System.out.println(" - Create Household");
                        createHousehold(ctx, msgFromClient);
                        break;
                    case OPCODE_FORGOT_PASSWORD:
                        forgotPassword(ctx, msgFromClient);
                }
            });
        });
        System.out.println("END MAIN METHOD");

        startTimerLoop();
        changerLoop();
    }

    private static void startTimerLoop() {
        Thread timerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Calendar cal;
                while (true) {
                    cal = Calendar.getInstance();
                    for (Device d : deviceList) {
                        if (d.getTimer() != 0 && cal.getTimeInMillis() > d.getTimer() ) {
                            System.out.println("# TimerLoop turning off timer for: " + d.getDeviceId() + " - " + d.getName());
                            d.setTimer(0);
                            Device device = d;
                            device.setValue(0);
                            device.setTimer(0);
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("data", gson.toJson(d));
                            changeDeviceStatus(null, jsonObject);
                        }
                    }
                }
            }
        });
        timerThread.start();
    }

    private static void changerLoop() {
        Scanner scanner = new Scanner(System.in);
        int id, value;
        while (true) {
            System.out.println("Enter deviceId:");
            id = scanner.nextInt();
            if (id == -1) {
                continue;
            }

            System.out.println("Enter new value:");
            value = scanner.nextInt();
            if (value == -1) {
                continue;
            }

            deviceChanger(id, value);
        }
    }

    private static void deviceChanger(int id, int value) {
        System.out.println("## DeviceChanger ##");
        for (Device d : deviceList) {
            if (d.getDeviceId() == id) {
                System.out.println(" - found device: " + d.getName());
                System.out.println(" - new value: " + value);
                d.setValue(value);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("opcode", OPCODE_CHANGE_DEVICE_STATUS);
                jsonObject.put("data", gson.toJson(d));
                changeDeviceStatus(null, jsonObject);
                return;
            }
        }
        System.out.println(" - Did not find device: " + id);
    }

    ////////////// House auth methods ///////////////////////////////////////////
    private static void createHousehold(WsContext ctx, JSONObject msgFromClient) {
        //Get new household object from jsonObject
        Household receivedHousehold = gson.fromJson(String.valueOf(msgFromClient.get("data")), Household.class);

        //Create household in database
        receivedHousehold.setHouseholdId(householdList.size() + 1);
        householdList.add(receivedHousehold);

        //Get new household object from database (now with id)
        Household newHousehold = receivedHousehold; //TODO: Placeholder, get from database

        //Return new household
        System.out.println("Houseauth: Created new Household: (" + newHousehold.getHouseholdId() + ")" + newHousehold.getName());
        System.out.println("Houseauth: Total number of Households are now: " + householdList.size());
        ctx.send(
                new JSONObject()
                        .put("opcode", OPCODE_CREATE_HOUSEHOLD_OK)
                        .put("data", gson.toJson(newHousehold))
                        .toString()
        );
        System.out.println("Houseauth: Sent household");
    }


    private static void createUser(WsContext ctx, JSONObject msgFromClient) {
        //Get new user object from jsonObject
        User receivedUser = gson.fromJson(String.valueOf(msgFromClient.get("data")), User.class);
        boolean userOk = true;
        boolean householdOk = false;

        //Check User in Database
        for (User user : userList) {
            if (receivedUser.getUsername().equals(user.getUsername())) {
                userOk = false;
            }
        }

        for(Household household : householdList) {
            if (receivedUser.getHouseholdId() == household.getHouseholdId()) {
                householdOk = true;
            }
        }

        //  If error creating user (such as existing username), send error code
        if (!userOk || !householdOk) {
            System.out.println("Houseauth: failed creating user");
            ctx.send(
                    new JSONObject()
                            .put("opcode", OPCODE_CREATE_USER_NOTOK)
                            .put("data", "")    //Room for error message
                            .toString()
            );
            return;
        }

        //Create user in database
        receivedUser.setUserId(userList.size() + 1);
        userList.add(receivedUser);

        //Get new user object from database (without password, but now with id)
        User newUser = receivedUser; //TODO: Placeholder, get from database

        //Return new user
        System.out.println("Houseauth: Created new user: (" + newUser.getUserId() + ")" + newUser.getUsername());
        System.out.println("Houseauth: Total number of users are now: " + userList.size());
        ctx.send(
                new JSONObject()
                        .put("opcode", OPCODE_CREATE_USER_OK)
                        .put("data", gson.toJson(newUser))
                        .toString()
        );
    }

    private static void requestToken(WsContext ctx, JSONObject msgFromClient) {
        String token = "";
        //Get user from JsonObject
        User receivedUser = gson.fromJson(String.valueOf(msgFromClient.get("data")), User.class);
        System.out.println("HouseAuth: Message: " + msgFromClient);

        //Find user in database
        //If invalid username/password, send error code
        boolean credentialsOk = false;
        for (User user : userList) {
            if (receivedUser.getUsername().equals(user.getUsername())) {
                if (receivedUser.getPassword().equals(user.getPassword())) {
                    credentialsOk = true;
                    break;
                }
            }
        }

        if (!credentialsOk) {
            System.out.println("Houseauth: requestToken: invalid: " + receivedUser.getUsername());
            ctx.send(
                    new JSONObject()
                            .put("opcode", OPCODE_NEW_TOKEN_NOTOK)
                            .put("data", "Invalid credentials")    //Room for error message
                            .toString()
            );
            return;
        }
        //Generate token
        token = receivedUser.getUsername() + "$" + receivedUser.getPassword();
        System.out.println("Houseauth: requestToken: Sending: " + token);

        //Send token
        ctx.send(
                new JSONObject()
                        .put("opcode", OPCODE_NEW_TOKEN)
                        .put("data", token)
                        .toString()
        );
    }

    private static void forgotPassword(WsContext senderCtx, JSONObject msgFromClient) {
        String username = msgFromClient.get("data").toString();
        User user = null;

        // Find user in database, do nothing if not found
        Boolean userFound = false;
        for (User u : userList) {
            if (u.getUsername().equals(username)) {
                userFound = true;
                user = u;
                break;
            }
        }
        if (!userFound) {
            System.out.println("[ForgotPassword] User not found: " + username);
            return;
        }

        // Send email & update password in database
        EmailService emailService = new EmailService();
        user.setPassword(emailService.forgotPassword(username));
    }

    ///////////////////// House methods //////////////////////////////
    private static void changeUserHousehold(WsContext senderCtx, JSONObject msgFromClient) {
        int newHouseholdId = msgFromClient.getInt("data");

        //Check if household exists in database
        Boolean householdExists = false;
        for (Household h : householdList) {
            if (h.getHouseholdId() == newHouseholdId) {
                householdExists = true;
            }
        }
        if (!householdExists) {
            //If not exist, send error
            senderCtx.send(
                    new JSONObject()
                            .put("opcode", OPCODE_USER_CHANGE_HOUSEHOLD_NOTOK)
                            .put("data", "Household could not be changed")
                            .toString()
            );
            sendAllDevice(senderCtx);
            return;
        }

        //Change for user in active session
        User user = userUsernameMap.get(senderCtx);
        user.setHouseholdId(newHouseholdId);

        //Change in database
        for (User u : userList) {
            if (u.getUserId() == user.getUserId()) {
                u.setHouseholdId(newHouseholdId);
                break;
            }
        }

        //Send all devices to user
        sendAllDevice(senderCtx);
    }

    private static void changeDeviceInfo(WsContext senderCtx, JSONObject msgFromClient) {
        Device device = gson.fromJson(String.valueOf(msgFromClient.get("data")), Device.class);

        // hämta device i databas, få command
        // uppdatera databas
        //


        //Find in DB: Device where deviceId == device.getDeviceId()
        boolean error = false;
        for (Device d : deviceList) {
            System.out.println(" - changeDeviceInfo method, looking at: " + d.getDeviceId() + ", looking for: " + device.getDeviceId());
            if (d.getDeviceId() == device.getDeviceId()) {
                System.out.println(" - Found device. Updating device value");
                if (userUsernameMap.get(senderCtx).getHouseholdId() != d.getHouseholdId())
                    //Update in database
                    d.setHouseholdId(device.getHouseholdId());
                d.setName(device.getName());
                d.setType(device.getType());
                break;
            }
        }

        //If error
        senderCtx.send(
                new JSONObject()
                        .put("opcode", OPCODE_CHANGE_DEVICE_INFO_NOTOK)
                        .put("data", "Could not change device")
                        .toString()
        );

        //Broadcast device update to all connections
        userUsernameMap.keySet().stream().filter(ctx -> ctx.session.isOpen()
                // && userUsernameMap.get(ctx).getHouseholdId() == device.getHouseholdId()
        ).forEach(session -> {
            session.send(
                    new JSONObject()
                            .put("opcode", OPCODE_STATUS_DEVICE)
                            .put("data", gson.toJson(device))
                            .toString()
            );
        });
    }


    private static void changeDeviceStatus(WsContext senderCtx, JSONObject msgFromClient) {
        Device device = gson.fromJson(String.valueOf(msgFromClient.get("data")), Device.class);

        // hämta device i databas, få command
        // skicka command till simpis
        // - call socket object
        // ta emot bekräftelse från simpis
        // - returned from socket object
        // uppdatera databas
        //


        //Find in DB: Device where deviceId == device.getDeviceId()
        for (Device d : deviceList) {
            System.out.println(" - changeDevice method, looking at: " + d.getDeviceId() + ", looking for: " + device.getDeviceId());
            if (d.getDeviceId() == device.getDeviceId()) {
                System.out.println(" - Found device. Updating device value");


                //Communicate with Arduino Server
                //Update in database
                if (device.getTimer() != 0) {
                    d.setValue(1);
                    device.setValue(1);
                    d.setTimer(device.getTimer());
                } else {
                    d.setValue(device.getValue());
                }
                break;
            }
        }

        //Print to log
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("opcode", OPCODE_STATUS_DEVICE)
                .put("data", gson.toJson(device));
        System.out.println(" - Sending: " + jsonObject);

        //Broadcast device update to all connections
        userUsernameMap.keySet().stream().filter(ctx -> ctx.session.isOpen()).forEach(session -> {
            session.send(
                    new JSONObject()
                            .put("opcode", OPCODE_STATUS_DEVICE)
                            .put("data", gson.toJson(device))
                            .toString()
            );
        });
    }

    private static void sendAllDevice(WsContext ctx) {
        int householdId = userUsernameMap.get(ctx).getHouseholdId();
        User user = userUsernameMap.get(ctx);
        Household household = null;

        JSONArray jsonArray = new JSONArray();
        //Find in DB: all devices with householdId
        for (Device d : deviceList) {
            if (d.getHouseholdId() == householdId) {
                jsonArray.put(gson.toJson(d));
            }
        }

        for (Household h : householdList) {
            if (h.getHouseholdId() == householdId) {
                household = h;
            }
        }

        //Send all devices to one connection
        JSONObject jsonObject = new JSONObject();
        ctx.send(
                jsonObject
                        .put("opcode", OPCODE_STATUS_ALL_DEVICES)
                        .put("data", jsonArray)
                        .put("household", gson.toJson(household))
                        .put("nameOfUser", user.getName())
                        .toString()
        );
        //Print to log
        System.out.println(" - Sending: \n" + jsonObject);
    }

    private static void sendInvalidTokenResponse(WsContext ctx) {
        System.out.println(" - Sending 'invalid response' message");
        //Send all devices to one connection
        ctx.send(
                new JSONObject()
                        .put("opcode", OPCODE_INVALID_TOKEN)
                        .put("data", "Invalid token")
                        .toString()
        );
    }
}