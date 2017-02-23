package com.github.unchama.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.github.unchama.gigantic.Gigantic;



public abstract class TableManager {
	protected Gigantic plugin = Gigantic.plugin;
	private Sql sql;
	protected final String db;
	private Connection con;
	protected Statement stmt = null;
	protected ResultSet rs;
	protected final String table;

	public TableManager(Sql sql){
		this.sql = sql;
		this.db = sql.getDataBaseName();
		this.con = sql.getConnection();
		this.table = Sql.TableManagerType.getTableNamebyClass(this.getClass());
		this.checkStatement();
		this.createTable();
	}

	abstract Boolean createTable();


	protected void checkStatement(){
		try {
			if(stmt == null || stmt.isClosed()){
				plugin.getLogger().warning("(" + table + ")Statement is Closed. Creating Statement...");
				stmt = con.createStatement();
			}
		} catch (SQLException e) {
			plugin.getLogger().warning("Statement is Closed by Fatal Error. ReConnecting MySql...");
			sql.checkConnection();
			e.printStackTrace();
		}
	}


	/**コマンド出力関数
	 *
	 * @param command
	 * @return
	 */
	protected boolean sendCommand(String command){
		checkStatement();
		try {
			stmt.executeUpdate(command);
			return true;
		}catch (SQLException e) {
			plugin.getLogger().warning("Failed to send Command in " + table + " Table");
			e.printStackTrace();
			return false;
		}
	}

}