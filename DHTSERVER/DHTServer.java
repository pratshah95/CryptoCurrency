/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package DHTServers;

import DNSSERVER.DNSServer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author Pratham
 */
public class DHTServer extends Thread {

    int port;
    ServerSocket server;
    String myHash;
    ArrayList<dhtNodeHash> dhtNodes = new ArrayList<>();
    HashMap<String, String> publicKeyMap = new HashMap<>();

    class dhtNodeHash {

        String nodeId;
        String nodeIdHash;
        int heartbeat;

        dhtNodeHash(String nodeId) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            this.nodeId = nodeId;
            nodeIdHash = encryptPassword(nodeId + "");
            heartbeat = 0;
        }
    }

    class sortNodes implements Comparator<dhtNodeHash> {

        public int compare(dhtNodeHash one, dhtNodeHash two) {
            return one.nodeIdHash.compareTo(two.nodeIdHash);
        }
    }

    public static String encryptPassword(String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String sha1 = "";
        MessageDigest crypt = MessageDigest.getInstance("SHA-1");
        crypt.reset();
        crypt.update(password.getBytes("UTF-8"));
        sha1 = byteToHex(crypt.digest());
        return sha1;
    }

    public static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    DHTServer(int port) throws IOException, NoSuchAlgorithmException {
        this.port = port;
        server = new ServerSocket(port, 100);
        myHash = encryptPassword(port + "");
    }

    public void run() {
        Thread listen = new listenDHTRequest();
        listen.start();
        Thread dhtList = new fetchDHTServerList();
        dhtList.start();
        Thread heartbeat = new HeartBeat();
        heartbeat.start();
    }

    DataOutputStream createOutputStream(Socket socket) throws IOException {
        OutputStream outToServerr = socket.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServerr);
        return out;
    }

    DataInputStream createInputStream(Socket socket) throws IOException {
        InputStream outToServerr = socket.getInputStream();
        DataInputStream in = new DataInputStream(outToServerr);
        return in;
    }

    < E> void addPropertyToJSONArray(String property, E value, JSONObject json) {
        json.put(property, value);
    }

    JSONObject parseJSON(String json) {
        JSONObject obj2;
        try {
            JSONParser parser = new JSONParser();
            JSONArray a = (JSONArray) parser.parse(json);
            obj2 = (JSONObject) a.get(0);
        } catch (ParseException ex) {
            obj2 = null;
            Logger.getLogger(DNSServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj2;
    }

    void addDHTServers(JSONArray dhtServers) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        synchronized (dhtNodes) {
            int n = dhtServers.size();
            for (int i = 0; i < n; i++) {
                dhtNodeHash node = new dhtNodeHash(dhtServers.get(i).toString());
                dhtNodes.add(node);
            }
            Collections.sort(dhtNodes, new sortNodes());
            int c = dhtNodes.size();
            for (int i = 0; i < c; i++) {
                System.out.println(dhtNodes.get(i).nodeId);
            }

        }
    }

    class fetchDHTServerList extends Thread {

        public void run() {
            try {
                Socket socket = new Socket("localhost", 60500);
                DataOutputStream write = createOutputStream(socket);
                JSONObject obj = new JSONObject();
                addPropertyToJSONArray("type", "GET", obj);
                addPropertyToJSONArray("dht", "1", obj);
                write.writeUTF("[" + obj.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                JSONObject json = parseJSON(in.readUTF());
                JSONArray dhtServers = (JSONArray) json.get("dhtServers");
                try {
                    addDHTServers(dhtServers);
                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    class listenDHTRequest extends Thread {

        public void run() {
            while (true) {
                try {
                    Socket socket = server.accept();
                    Thread serveRequest = new serveRequest(socket);
                    serveRequest.start();
                } catch (IOException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class serveRequest extends Thread {

        Socket socket;

        public serveRequest(Socket socket) {
            this.socket = socket;
        }

        String readRequest(Socket server) throws IOException {
            String request = "";
            InputStream inFromServer = server.getInputStream();
            DataInputStream in = new DataInputStream(inFromServer);
            request = in.readUTF();
            return request;
        }

        void sendResponse(String response, Socket socket) throws IOException {
            OutputStream outToServer = socket.getOutputStream();
            DataOutputStream out = new DataOutputStream(outToServer);
            out.writeUTF(response);
            socket.close();
        }

        int getNearestServer(String key) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            int nearest = -1;
            key = encryptPassword(key);
            synchronized (dhtNodes) {
                int n = dhtNodes.size();
                for (int i = 0; i < n; i++) {
                    if (key.compareTo(dhtNodes.get(i).nodeIdHash) > 0) {
                        nearest = i;
                        break;
                    }
                }
                if (nearest == -1) {
                    nearest = n;
                }
            }
            return nearest;
        }

        class insertBackup extends Thread {

            JSONObject request;
            int port;

            public insertBackup(JSONObject request, int port) {
                this.request = request;
                this.port = port;
            }

            public void run() {
                try {
                    Socket socket = new Socket("localhost", port);
                    DataOutputStream out = createOutputStream(socket);
                    JSONObject requestBackup = new JSONObject();
                    requestBackup.put("type", "POST");
                    requestBackup.put("key", request.get("key").toString());
                    requestBackup.put("value", request.get("value").toString());
                    requestBackup.put("backup", "1");
                    out.writeUTF("[" + requestBackup.toJSONString() + "]");
                    DataInputStream in = createInputStream(socket);
                    System.out.println(in.readUTF() + " " + port);
                    socket.close();
                } catch (IOException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        String createResponse(String request) throws NoSuchAlgorithmException, UnsupportedEncodingException, IOException {
            String response = "";
            JSONObject requestJSON = parseJSON(request);
            //  System.out.println(requestJSON.get("key").toString());
            JSONObject responseJson = new JSONObject();
            if (requestJSON.get("type").equals("POST") && requestJSON.get("key") != null && requestJSON.get("value") != null) {
                if (requestJSON.get("backup") == null) {
                    int serverAddress = getNearestServer(requestJSON.get("key").toString());
                    int n = dhtNodes.size();

                    if (Integer.parseInt(dhtNodes.get(serverAddress).nodeId) == port) {

                        publicKeyMap.put(requestJSON.get("key").toString(), requestJSON.get("value").toString());
                        Thread backup1 = new insertBackup(requestJSON, Integer.parseInt(dhtNodes.get((serverAddress + 1) % n).nodeId));
                        backup1.start();
                        Thread backup2 = new insertBackup(requestJSON, Integer.parseInt(dhtNodes.get((serverAddress == 0 ? (n - 1) : serverAddress - 1)).nodeId));
                        backup2.start();
                        responseJson.put("success", "1");
                        responseJson.put("inserted", "1");
                    } else {
                        responseJson.put("success", "1");
                        responseJson.put("inserted", "0");
                        responseJson.put("address", dhtNodes.get(serverAddress).nodeId);
                    }
                } else {
                    publicKeyMap.put(requestJSON.get("key").toString(), requestJSON.get("value").toString());
                    responseJson.put("success", "1");
                    responseJson.put("inserted", "1");
                }
                response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("GET") && requestJSON.get("key") != null) {                  
                  int serverAddress = getNearestServer(requestJSON.get("key").toString());
                  responseJson.put("success","1");                                    
                  if (Integer.parseInt(dhtNodes.get(serverAddress).nodeId) == port) {
                  responseJson.put("nearest","1");    
                  responseJson.put("value", publicKeyMap.get(requestJSON.get("key").toString()));                  
                  }else{
                  responseJson.put("nearest","0");
                  responseJson.put("address", dhtNodes.get(serverAddress).nodeId);
                  }
                  response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("heartbeat")) {
                responseJson.put("success", "1");
                response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("DELETE") && requestJSON.get("dht") != null) {
                responseJson.put("success", "1");
                synchronized (dhtNodes) {
                    int n = dhtNodes.size();
                    for (int i = 0; i < n; i++) {
                        if (dhtNodes.get(i).nodeId.equals(requestJSON.get("dht").toString())) {
                            dhtNodes.remove(i);
                            break;
                        }
                    }
                }
                response = "[" + responseJson.toJSONString() + "]";
            }
            return response;
        }

        public void run() {
            try {
                String request = readRequest(socket);
                String response = createResponse(request);
                sendResponse(response, socket);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class nodeFailure extends Thread {

        String nodeId;

        nodeFailure(String nodeId) {
            this.nodeId = nodeId;
        }

        public void run() {
            for (int i = 0; i < dhtNodes.size(); i++) {
                try {
                    Socket socket = new Socket("localhost", Integer.parseInt(dhtNodes.get(i).nodeId));
                    DataOutputStream out = createOutputStream(socket);
                    JSONObject deleteDHT = new JSONObject();
                    deleteDHT.put("type", "DELETE");
                    deleteDHT.put("dht", nodeId);
                    out.writeUTF("[" + deleteDHT.toJSONString() + "]");
                    DataInputStream in = createInputStream(socket);
                    in.readUTF();
                } catch (IOException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class HeartBeat extends Thread {

        JSONObject createRequestJSON() {
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("type", "heartbeat");
            return jsonRequest;
        }

        public void run() {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            while (true) {
                for (int i = 0; i < dhtNodes.size(); i++) {
                    dhtNodeHash node = dhtNodes.get(i);
                    if (Integer.parseInt(node.nodeId) != port) {
                        try {

                            Socket socket = new Socket("localhost", Integer.parseInt(node.nodeId));
                            DataOutputStream out = createOutputStream(socket);
                            JSONObject request = createRequestJSON();
                            out.writeUTF("[" + request.toJSONString() + "]");
                            DataInputStream in = createInputStream(socket);
                            JSONObject response = parseJSON(in.readUTF());
                            if (response.get("success").equals("1")) {
                                node.heartbeat = 0;
                            }

                        } catch (Exception ex) {
                            node.heartbeat++;

                            if (node.heartbeat > 3) {

                                /*int n = dhtNodes.size();
                                 int after = -1;
                                 int before = -1;
                                 for (int j = 0; j < before; j++) {
                                 if (dhtNodes.get(j).nodeId.equals(node.nodeId)) {
                                 after = (j + 1) % n;
                                 before = (j - 1) % n;
                                 }
                                 }
                                 if(dhtNodes.get(before).nodeId.equals(port+"")||dhtNodes.get(after).nodeId.equals(port+"")){
                                   
                                 }*/
                                Thread nodeFailure = new nodeFailure(node.nodeId);
                                nodeFailure.start();
                            }

                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
                Random random = new Random();
                int delay = 10000 + random.nextInt(10000);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public static void main(String args[]) throws IOException, NoSuchAlgorithmException {
        for (int i = 0; i < 4; i++) {
            Thread dhtserver = new DHTServer(60101 + i);
            dhtserver.start();
        }

    }
}