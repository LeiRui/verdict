import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FirstVerdictDBExample {


  public static void main(String args[]) throws SQLException {
    // Suppose username is root and password is rootpassword.
    Connection mysqlConn =
        DriverManager.getConnection("jdbc:mysql://localhost", "root", "root");
    Statement stmt = mysqlConn.createStatement();
    stmt.execute("CREATE SCHEMA myschema");
    stmt.execute("CREATE TABLE myschema.sales (" +
        "  product   varchar(100)," +
        "  price     double)");

    // insert 1000 rows
    List<String> productList = Arrays.asList("milk", "egg", "juice");
    for (int i = 0; i < 1000; i++) {
      int randInt = ThreadLocalRandom.current().nextInt(0, 3);
      String product = productList.get(randInt);
      double price = (randInt+2) * 10 + ThreadLocalRandom.current().nextInt(0, 10);
      stmt.execute(String.format(
          "INSERT INTO myschema.sales (product, price) VALUES('%s', %.0f)",
          product, price));
    }

    Connection verdict =
        DriverManager.getConnection("jdbc:verdict:mysql://localhost", "root", "root");
    Statement vstmt = verdict.createStatement();

    // Use CREATE SCRAMBLE syntax to create scrambled tables.
    vstmt.execute("CREATE SCRAMBLE myschema.sales_scrambled from myschema.sales");

    ResultSet rs = vstmt.executeQuery(
        "SELECT product, AVG(price) "+
            "FROM myschema.sales " +
            "GROUP BY product " +
            "ORDER BY product");

    stmt.execute(String.format("drop schema myschema"));
  }
}