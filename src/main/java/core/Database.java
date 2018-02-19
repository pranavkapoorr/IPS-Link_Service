package core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.mysql.jdbc.PreparedStatement;


public class Database {

	private Connection getConnection(){
		Connection connection = null;
		try{
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			connection = DriverManager.getConnection( "jdbc:mysql://localhost:3306/ips","root","");  
		}catch (Exception e) {
			System.err.println(e.getMessage());
		}
		return connection;
	}
	public void insertData(String[] date_time, int terminalId, String operation, String result){  
		try{  
			Connection con = getConnection();
			String query = "insert into details values(?,?,?,?,?)";
			PreparedStatement statement = (PreparedStatement) con.prepareStatement(query);
			statement.setString(1, date_time[0]);
			statement.setString(2, date_time[1]);
			statement.setInt(3, terminalId);
			statement.setString(4, operation);
			statement.setString(5, result);
			if(statement.executeUpdate()>0){
				System.out.println("written to db");
			}else{
				System.err.println("couldnt write");
			}
			
			con.close();  
		}catch(Exception e){ System.out.println(e);}  
	}  
	public void insertConnection(String time,String ipAddress, String port) throws SQLException{
		Connection con = getConnection();
		String query = "insert into connections values(?,?,?)";
		PreparedStatement statement = (PreparedStatement) con.prepareStatement(query);
		statement.setString(1, time);
		statement.setString(2, ipAddress);
		statement.setString(3, port);
		if(statement.executeUpdate()>0){
			System.out.println("connection added");
		}else{
			System.err.println("couldnt write");
		}
		
		con.close();  
	}
	public void removeConnection(String ipAddress, String port) throws SQLException{
		Connection con = getConnection();
		String query = "delete from connections where ip = ? and port = ?";
		PreparedStatement statement = (PreparedStatement) con.prepareStatement(query);
		statement.setString(1, ipAddress);
		statement.setString(2, port);
		if(statement.executeUpdate()>0){
			System.out.println("connection removed");
		}else{
			System.err.println("couldnt remove");
		}
		
		con.close();  
	}
}  