package com.lapsedpacifist.astro.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lapsedpacifist.astro.novas.core.Novas;

public class Time extends Date {

	private static Logger logger = LoggerFactory.getLogger(Time.class);
	private static HashMap<Time,Double> deltaAT = new HashMap<Time,Double>();
	private static boolean deltaATInternetLoaded = false;
	private static Time latestTimeForDeltaAT = null;
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Returns an instance of Time corresponding with the specified Julian date
	 * in Coordinated Universal Time (UTC). 
	 * @param jd The Julian date in UTC.
	 * @return The time.
	 */
	public static Time getTimeForJulianDateUTC(double jd){
		long tm = Time.calculateTime(jd);
		Time time = new Time(tm);
		return time;
	}
	
	/**
	 * Returns an instance of Time corresponding with the specified Julian date
	 * in International Atomic Time. 
	 * @param jd The Julian date in TAI.
	 * @return The time.
	 */
	public static Time getTimeForJulianDateTAI(double jd){
		long tm = Time.calculateTime(jd);
		Time temp = new Time(tm);
		long dat = Time.getDeltaAT(temp);
		return new Time(tm-dat);
	}
	
	/**
	 * Returns an instance of Time corresponding with the specified Julian date
	 * in Terrestrial Time. 
	 * @param jd The Julian date in TT.
	 * @return The time.
	 */
	public static Time getTimeForJulianDateTT(double jd){
		long tm = Time.calculateTime(jd);
		Time temp = new Time(tm);
		long dat = Time.getDeltaAT(temp);
		return new Time(tm-dat-32184);
	}
	
	/**
	 * Creates a new Time instance corresponding with the current time.
	 */
	public Time(){
		super();
	}
	
	/**
	 * Creates a new Time instance with the specified number of milliseconds
	 * since January 1, 1970 0h UTC.
	 * @param millisecondsSince1970 The time.
	 */
	public Time(long millisecondsSince1970){
		super(millisecondsSince1970);
	}

	/**
	 * This method returns the time as the number of milliseconds since 
	 * January 1, 1970 at 0h Coordinated Universal Time (UTC).
	 * @return The time as number of milliseconds in UTC.
	 */
	public long getTimeUTC(){
		return super.getTime();
	}
	
	/**
	 * Returns the Julian date in Coordinated Universal Time (UTC).
	 * @return The Julian date in UTC.
	 */
	public double getJulianDateUTC(){
		return Time.calculateJulianDate(this.getTimeUTC());
	}

	/**
	 * This method returns the time as the number of milliseconds 
	 * since January 1, 1970 at 0h International Atomic Time (TAI).
	 * @return The time as number of milliseconds in TAI.
	 */
	public long getTimeTAI(){
		long dAT = Time.getDeltaAT(this);
		return super.getTime()+dAT;
	}
	
	/**
	 * Returns the Julian date in International Atomic Time (TAI).
	 * @return The Julian date (TAI).
	 */
	public double getJulianDateTAI(){
		return Time.calculateJulianDate(this.getTimeTAI());
	}

	/**
	 * This method returns the time as the number of milliseconds 
	 * since January 1, 1970 at 0h Terrestrial Time (TT).
	 * @return The time as number of milliseconds in TT.
	 */
	public long getTimeTT(){
		return this.getTimeTAI()+32184l;
	}
	
	/**
	 * Returns the Julian date in Terrestrial Time (TT).
	 * @return The Julian date in TT.
	 */
	public double getJulianDateTT(){
		return Time.calculateJulianDate(this.getTimeTT());
	}

	/**
	 * This method returns the time as the number of milliseconds 
	 * since January 1, 1970 at 0h Barycentric Dynamical Time (TDB).
	 * @return The time as number of milliseconds in TDB.
	 */
	public long getTimeTDB(){
		long tt = this.getTimeTT();
		double dtdbtt = Novas.getNOVAS().getTDBtoTTSecondDifference(Time.calculateJulianDate(tt));
		long tdb = tt+(long)(dtdbtt*1000.0);
		return tdb;
	}
	
	/**
	 * Returns the Julian date in Barycentric Dynamical Time (TDB).
	 * @return The Julian date in TDB.
	 */
	public double getJulianDateTDB(){
		return Time.calculateJulianDate(this.getTimeTDB());
	}
	
	private static double calculateJulianDate(long time){
		double days = (double)(time)/86400000;
		return 2440587.500000+days; // 2440587.500000 is the JD at 1970-Jan-1-0h UTC
	}
	
	private static long calculateTime(double jd){
		double days = jd-2440587.500000;
		long time = (long)(days*86400000);
		return time;
	}
	
	/** 
	 * Finds the difference \Delta AT = TAI-UTC at the specified date in UTC.
	 * @param cdate The date for which the difference is to be found.
	 * @return The difference TAI-UTC.
	 */
	private static long getDeltaAT(Date cdate){
		Time.loadDeltaAT(cdate);
		Set<Time> dates = deltaAT.keySet();
		double dat = 0;
		for(Time date : dates){
			if(date.after(cdate)){
				break;
			}
			dat = deltaAT.get(date).intValue();
		}
		return (long)(dat*1000);
	}
	
	private static void loadDeltaAT(Date date){
		if(deltaAT.size()==0){
			try {
				HashMap<Time,Double> tmp = loadDeltaATFile(Time.class.getResourceAsStream("/com/lapsedpacifist/astro/resources/tai-utc.dat"));
				deltaAT = tmp;
				deltaATInternetLoaded = false;
			} catch (IOException e) {
				logger.error("Could not load TAI-UTC values file.",e);
				deltaAT = null;
			}
		}
		// Load from internet if loading from jar did not work or if the date is after the last file date.
		if(deltaAT == null || (date.after(latestTimeForDeltaAT) && !deltaATInternetLoaded)){
			long dif = 10000000000l;
			if(latestTimeForDeltaAT!=null) dif = date.getTime()-latestTimeForDeltaAT.getTimeUTC();
			// test if the difference is more than a half year.
			if(dif>15638400){
				HashMap<Time, Double> tmp;
				try {
					tmp = loadDeltaATFile((new URL("http://maia.usno.navy.mil/ser7/tai-utc.dat")).openStream());
					if(tmp.size()<0){
						deltaAT = tmp;
						deltaATInternetLoaded = true;
					}
				} catch (Exception e) {
					logger.error("Could not load TAI-UTC values file.",e);
				} 
			}
		}
	}
	
	private static HashMap<Time,Double> loadDeltaATFile(InputStream stream) throws IOException{
		HashMap<Time,Double> tmp = new HashMap<Time,Double>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		String line;
		while((line = reader.readLine())!=null){
			String jdstr = line.substring(16, 12);
			double jd = new Double(jdstr).doubleValue();
			String dstr = line.substring(36,13);
			Double d = new Double(dstr);
			Time time = Time.getTimeForJulianDateUTC(jd);
			tmp.put(time, d);
			latestTimeForDeltaAT = time;
		}
		return tmp;
	}
}
