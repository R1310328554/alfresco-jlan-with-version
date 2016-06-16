package com.util;

public class AESTag {
	private boolean isSecret = false;//false|true
	private String nLeve="S";//F全部,S开始,E结束
	private String key1;//public
	private String key2;//private
	private String bit = "128";//128|256
	private int infoLen = 0;
	private int headerLen = 0;
	private long p;//原文位置开始
	private long pend;//原文位置结束
	private long encryptPos;//密文位置开始
	private long encryptPosEnd;//密文位置结束
	
	public boolean isSecret() {
		return isSecret;
	}
	public void setSecret(boolean isSecret) {
		this.isSecret = isSecret;
	}
	public String getnLeve() {
		return nLeve;
	}
	public void setnLeve(String nLeve) {
		this.nLeve = nLeve;
	}
	public String getKey1() {
		return key1;
	}
	public void setKey1(String key1) {
		this.key1 = key1;
	}
	public String getKey2() {
		return key2;
	}
	public void setKey2(String key2) {
		this.key2 = key2;
	}
	public String getBit() {
		return bit;
	}
	public void setBit(String bit) {
		this.bit = bit;
	}
	public int getInfoLen() {
		return infoLen;
	}
	public void setInfoLen(int infoLen) {
		this.infoLen = infoLen;
	}
	public int getHeaderLen() {
		return infoLen+30;
	}
	public long getP() {
		return p;
	}
	public void setP(long p) {
		this.p = p;
	}
	public long getPend() {
		return pend;
	}
	public void setPend(long pend) {
		this.pend = pend;
	}
	public long getEncryptPos() {
		return encryptPos;
	}
	public void setEncryptPos(long encryptPos) {
		this.encryptPos = encryptPos;
	}
	public long getEncryptPosEnd() {
		return encryptPosEnd;
	}
	public void setEncryptPosEnd(long encryptPosEnd) {
		this.encryptPosEnd = encryptPosEnd;
	}
}
