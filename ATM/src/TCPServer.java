package demo_network;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TCPServer {
    private Connection connection;

    private void connectToDatabase() {
        String url = "jdbc:mysql://127.0.0.1:3306/user_transactions";
        String user = "root";
        String password = "374970wyh";

        try {
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loginid(DataOutputStream outToClient) throws IOException {
        outToClient.writeBytes("500 AUTH REQUIRED!\n");
        outToClient.flush();
    }

    public void loginpassword(String id, int password, DataOutputStream outToClient) throws IOException {
        try {
            // 查询数据库，检索与给定用户名对应的密码
            PreparedStatement statement = connection.prepareStatement("SELECT password FROM users WHERE userid = ?");
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                String dbPassword = resultSet.getString("password");

                // 将数据库中的密码与客户端提供的密码进行比较
                if (password == Integer.parseInt(dbPassword)) {
                    outToClient.writeBytes("525 OK!\n");
                } else {
                    outToClient.writeBytes("401 ERROR\n");
                }
            } else {
                // 没有找到匹配的用户
                outToClient.writeBytes("402 USER NOT FOUND\n");
            }

            outToClient.flush();
        } catch (SQLException e) {
            e.printStackTrace();
            // 数据库查询出错
            outToClient.writeBytes("500 INTERNAL SERVER ERROR\n");
            outToClient.flush();
        }
    }
    public  void getBalance(String id, DataOutputStream outToClient) throws IOException {
        try {
            // 查询数据库，检索与给定用户名对应的余额

            PreparedStatement statement = connection.prepareStatement("SELECT money FROM users WHERE userid = ?");
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                int balance = resultSet.getInt("money");
                // 发送余额信息给客户端
                outToClient.writeBytes("AMNT:" + balance + "\n");
                outToClient.flush();
            } else {
                // 没有找到匹配的用户
                outToClient.writeBytes("402 USER NOT FOUND\n");
                outToClient.flush();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // 数据库查询出错
            outToClient.writeBytes("500 INTERNAL SERVER ERROR\n");
            outToClient.flush();
        }
    }
    public void withdraw(String id, int amount, DataOutputStream outToClient) throws IOException {
        try {
            // 查询数据库，检索与给定用户名对应的余额
            PreparedStatement statement = connection.prepareStatement("SELECT money FROM users WHERE userid = ?");
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                int balance = resultSet.getInt("money");
                if (balance >= amount) {
                    // 更新数据库中的余额信息
                    PreparedStatement withdrawStatement = connection.prepareStatement("UPDATE users SET money = money - ? WHERE userid = ?");
                    withdrawStatement.setInt(1, amount);
                    withdrawStatement.setString(2, id);
                    withdrawStatement.executeUpdate();

                    // 发送成功响应给客户端
                    outToClient.writeBytes("525 OK!\n");
                } else {
                    // 余额不足，发送错误响应给客户端
                    outToClient.writeBytes("401 ERROR!\n");
                }
            } else {
                // 没有找到匹配的用户
                outToClient.writeBytes("402 USER NOT FOUND\n");
            }

            outToClient.flush();
        } catch (SQLException e) {
            e.printStackTrace();
            // 数据库查询出错
            outToClient.writeBytes("500 INTERNAL SERVER ERROR\n");
            outToClient.flush();
        }
    }


    public  static void main(String argv[]) throws Exception {

        TCPServer server = new TCPServer(); // 创建 TCPServer 对象
        server.connectToDatabase(); // 连接数据库

        ServerSocket welcomeSocket = new ServerSocket(2525);
        String clientSentence;
        String id = null;

        while (true) {
            Socket connectionSocket = welcomeSocket.accept();
            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
            DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

            clientSentence = inFromClient.readLine();
            Pattern pattern = Pattern.compile("[A-Z]+");
            Matcher matcher = pattern.matcher(clientSentence);

            if (matcher.find()) {
                String request = matcher.group();
                if (request.equalsIgnoreCase("HELO")) {
                    server.loginid(outToClient);
                    String[] parts = clientSentence.split("\\s+");
                    if (parts.length >= 2) {
                        id = parts[1]; // 获取分割后的第二部分作为账号信息
                        System.out.println(id);
                    } else {
                        System.out.println("Invalid HELO request");
                    }
                } else if (request.equalsIgnoreCase("PASS")) {
                    if (id != null) {
                        String[] tokens = clientSentence.split("\\s+");
                        if (tokens.length == 2 && tokens[0].equalsIgnoreCase("PASS")) {
                            int password = Integer.parseInt(tokens[1]);
                            System.out.println(password);
                            server.loginpassword(id, password, outToClient);

                        } else {
                            outToClient.writeBytes("400 BAD REQUEST\n");
                            outToClient.flush();
                        }
                    } else {
                        // 提示客户端先发送账号信息
                        outToClient.writeBytes("401 ACCOUNT REQUIRED\n");
                        outToClient.flush();
                    }
                }else if (request.equalsIgnoreCase("BALA")) {
                    if (id != null) {
                        server.getBalance(id, outToClient);
                    } else {
                        // 提示客户端先发送账号信息
                        outToClient.writeBytes("401 ACCOUNT REQUIRED\n");
                        outToClient.flush();
                    }
                }else if (request.equalsIgnoreCase("WDRA")) {
                    if (id != null) {
                        String[] tokens = clientSentence.split("\\s+");
                        if (tokens.length == 2 && tokens[0].equalsIgnoreCase("WDRA")) {
                            int amount = Integer.parseInt(tokens[1]);
                            server.withdraw(id, amount, outToClient);
                        } else {
                            outToClient.writeBytes("400 BAD REQUEST\n");
                            outToClient.flush();
                        }
                    } else {
                        // 提示客户端先发送账号信息
                        outToClient.writeBytes("401 ACCOUNT REQUIRED\n");
                        outToClient.flush();
                    }
                }else if (request.equalsIgnoreCase("BYE")) {
                    outToClient.writeBytes("BYE\n");
                    outToClient.flush();
                    break; // 结束连接
                }


            }

            // 关闭连接
            connectionSocket.close();
            inFromClient.close();
            outToClient.close();
        }
    }

}
