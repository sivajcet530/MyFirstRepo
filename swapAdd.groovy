
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class swapAdd {
	

        private static Connection getConnection() throws Exception {

        Connection connection = null;
        try {
           String oracleDriver = "oracle.jdbc.driver.OracleDriver";
            Class.forName(oracleDriver);
            String url = "jdbc:oracle:thin:@172.25.224.102:1521:xe";
            connection = DriverManager.getConnection(url, "Genrocket", "Password123");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            throw new Exception(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e);
        }
        return connection;
    }   

public static void main(String[] args) {
		Connection connection = null;
		try {
			connection = getConnection();
			String sql = "SELECT DISTINCT PINCODE FROM ADDRESSSWAP";
			PreparedStatement pst = connection.prepareStatement(sql);
			ResultSet rs = pst.executeQuery();
			while (rs.next()) {
				String pinCode = (rs.getString("PINCODE"));
				String datafrmTable = "SELECT SUBSCRIBERID,ADDRESS1 FROM ADDRESSSWAP WHERE PINCODE=\'"+pinCode+"\'";
				PreparedStatement pst1 = connection.prepareStatement(datafrmTable);
				ResultSet rs1 = pst1.executeQuery();
				List<String> subid = new ArrayList<String>();
				List<String> address = new ArrayList<String>();
				while(rs1.next()) {
					address.add((rs1.getString("ADDRESS1")));
					subid.add((rs1.getString("SUBSCRIBERID")));
				}
				PreparedStatement pst2 = connection.prepareStatement("UPDATE ADDRESSSWAP SET ADDRESS1 =? WHERE SUBSCRIBERID =?");
				for (int i = 1; i<address.size();i++) {
					pst2.setString(1, address.get(i));
					pst2.setString(2, subid.get(i-1));
					pst2.executeUpdate();				
				}
				pst2.setString(1, address.get(0));
				pst2.setString(2, subid.get(address.size()-1));
				pst2.executeUpdate();	
				pst1.close();
				rs1.close();
			}
			pst.close();
			rs.close();
			connection.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
