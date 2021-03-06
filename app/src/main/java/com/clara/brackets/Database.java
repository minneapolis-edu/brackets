package com.clara.brackets;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.clara.brackets.data.Competitor;
import com.clara.brackets.data.Match;

import java.util.ArrayList;
import java.util.Date;

/**
 * SQLite interaction.  Save competitor list, and current status of tournament bracket
 *
 */

public class Database {

	private Context context;
	private SQLHelper helper;
	private SQLiteDatabase db;

	private final String TAG = "DATABASE";

	private  static final String DB_NAME = "brackets.db";

	private  static final int DB_VERSION = 1;

	//Two tables: one for Competitors
	//one for results of a particular Match

	public final String COMPETITORS_TABLE = "competitors";
	public final String COMP_ID = "_id";
	public final String COMPETITOR_NAME = "name";
	public final String COMP_IS_BYE = "is_bye";


	/*

	Competitor comp_1;  (save id)
	Competitor comp_2;  (save id)
	Competitor winner;  (save id)

	Date matchDate;
	int nodeId;

*/

	public static final String MATCHES_TABLE = "matches";

	public static final String MATCH_ID = "_id";   //primary key

	public	static final String COMP_1_ID = "competitor_1_id";
	public static final String COMP_2_ID = "competitor_2_id";
	public	static final String WINNER_ID = "winner_id";

	public static final String NODE_ID = "node_id";      //corresponds to node_id in Bracket tree
	public static final String MATCH_DATE = "match_date";


	public Database(Context c) {
		this.context = c;
		helper = new SQLHelper(c);
		this.db = helper.getWritableDatabase();
	}

	void close() {
		helper.close(); //Closes the database - important!
	}


	/** Insert competitors, read competitors */

	public int competitorCount() {

		Cursor cursor = db.rawQuery("SELECT COUNT (*) FROM " + COMPETITORS_TABLE, null);
		cursor.moveToFirst();
		int count = cursor.getInt(0);
		Log.d(TAG, "Number of competitors = " + count);
		return count;

	}

	public void saveNewCompetitors(ArrayList<Competitor> competitors) {

		Log.d(TAG, "About to save this to DB: " + competitors);

		for (Competitor c : competitors)  {

			ContentValues newCompetitorValues = new ContentValues();
			newCompetitorValues.put(COMPETITOR_NAME, c.name);
			newCompetitorValues.put(COMP_IS_BYE, c.bye);
			long pk = db.insertOrThrow(COMPETITORS_TABLE, null, newCompetitorValues);
			c.id = pk;
		}

		Log.d(TAG, "Saved this competitor to DB: " + competitors);
	}


	public ArrayList<Competitor> readCompetitors() {

		ArrayList<Competitor> competitors = new ArrayList<>();

		Cursor cursor = db.query(COMPETITORS_TABLE, null, null, null, null, null, null);

		while (cursor.moveToNext()) {

			long id = cursor.getLong(0);
			String name = cursor.getString(1);
			int bye = cursor.getInt(2);
			// 0 is false and 1 is true
			boolean isBye = (bye == 1);   //if bye == 1 then isBye should be true.

			Competitor comp = new Competitor(name, id, isBye);
			competitors.add(comp);
		}

		cursor.close();
		return competitors;


	}

	/** Matches */

	public void saveMatchCreateID(Match match) {

		ContentValues values = new ContentValues();
		match.addValues(values);
		long id = db.insert(MATCHES_TABLE, null, values);

		match.db_id = id;

	}


	public void updateBracketMatches(ArrayList<Match> matches) {

		Log.d(TAG, "Saving these matches: " + matches);

		for (Match match : matches) {

			ContentValues values = new ContentValues();
			match.addValues(values);
			String where = MATCH_ID + " = " + match.db_id;
			db.update(MATCHES_TABLE, values, where, null);

		}

	}


	public ArrayList<Match> getAllMatchesForBracket() {

		//populate comp_1 and comp_2 and winner with database queries

		ArrayList<Match> matches = new ArrayList<>();

		// Get everything from the Matches table
		Cursor c = db.query(MATCHES_TABLE, null, null, null, null, null, null);

		while (c.moveToNext()) {
			//make match object
			//query db to get competitor info
			Match match = new Match();
			match.db_id = c.getInt(0);

			int comp_1_id = c.getInt(1);
			if (comp_1_id != -1) {
				match.comp_1 = getCompetitorForId(comp_1_id);
			}

			int comp_2_id = c.getInt(2);
			if (comp_2_id != -1) {
				match.comp_2 = getCompetitorForId(comp_2_id);
			}

			int winner_id = c.getInt(3);
			if (winner_id != -1) {
				match.winner = getCompetitorForId(winner_id);
			}

			match.nodeId = c.getInt(4);

			long matchDate = c.getLong(5);
			//Create date, if one available
			if (matchDate != Match.NO_DATE) {
				match.matchDate = new Date(matchDate);
			}

			matches.add(match);

		}

		Log.d(TAG, "All matches from db: " + matches);
		c.close();

		return matches;

	}

	private Competitor getCompetitorForId(int pk_id) {

		Log.d(TAG, "Fetch competitor for id = " + pk_id);

		String where = COMP_ID + " = " + pk_id;
		Cursor cursor = db.query(COMPETITORS_TABLE, null, where, null, null, null, null);
		if (!cursor.moveToFirst()) {
			Log.e(TAG, "Result set empty for competitor with pk " + pk_id );
			return null;
		}

		long id = cursor.getLong(0);
		String name = cursor.getString(1);
		int bye = cursor.getInt(2);
		boolean isBye = (bye == 1);

		Competitor competitor = new Competitor(name, id, isBye);

		cursor.close();

		Log.d(TAG, "Fetched competitor = " + competitor);

		return competitor;

	}

	public void clearAll() {
		db.delete(MATCHES_TABLE, null, null);
		db.delete(COMPETITORS_TABLE, null, null);
	}


	private class SQLHelper extends SQLiteOpenHelper {

		private static final String SQL_TAG = "DB / SQL HELPER";

		SQLHelper(Context c){
			super(c, DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {

//			db.execSQL("DROP TABLE IF EXISTS " + COMPETITORS_TABLE);    ///this method only run when db upgraded.
//			db.execSQL("DROP TABLE IF EXISTS " + MATCHES_TABLE);

			String createCompetitorsBase = "CREATE TABLE %s ( %s INTEGER PRIMARY KEY AUTOINCREMENT, %s TEXT , %s INTEGER )";  //todo check SQL
			String createCompetitorsSQL = String.format(createCompetitorsBase, COMPETITORS_TABLE, COMP_ID, COMPETITOR_NAME, COMP_IS_BYE);

			String createMatchesBase = "CREATE TABLE %s " +
					"(%s INTEGER PRIMARY KEY AUTOINCREMENT, " +   //match id (pk)
					"%s INTEGER, %s INTEGER, " +     	//comp_1, comp_2
					"%s INTEGER, " +					//winner id
					" %s INTEGER, " +    				// node id
					"%s INTEGER " +						//match date (as timestamp)
					" )";

			String createMatchesSQL = String.format(createMatchesBase, MATCHES_TABLE, MATCH_ID, COMP_1_ID, COMP_2_ID, WINNER_ID, NODE_ID, MATCH_DATE);

			Log.d(SQL_TAG, createCompetitorsSQL);
			db.execSQL(createCompetitorsSQL);

			Log.d(SQL_TAG, createMatchesSQL);
			db.execSQL(createMatchesSQL);

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + COMPETITORS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + MATCHES_TABLE);

			onCreate(db);
			Log.w(SQL_TAG, "Upgrade table - drop and recreate it");
		}
	}
}
