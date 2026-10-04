package com.bhttp.util;

import com.bhttp.proto.Frame;
import com.bhttp.proto.FrameType;

public final class Hexdump {
    private Hexdump() {}
    public static String dump(String label, Frame f) {
        byte[] hdr = new byte[9];
        int len = f.payload().length;
        hdr[0]=(byte)((len>>>16)&0xFF); hdr[1]=(byte)((len>>>8)&0xFF); hdr[2]=(byte)(len&0xFF);
        hdr[3]=(byte)(f.type()&0xFF); hdr[4]=(byte)(f.flags()&0xFF);
        hdr[5]=(byte)((f.requestId()>>>24)&0xFF); hdr[6]=(byte)((f.requestId()>>>16)&0xFF);
        hdr[7]=(byte)((f.requestId()>>>8)&0xFF); hdr[8]=(byte)(f.requestId()&0xFF);
        byte[] all = new byte[9 + len];
        System.arraycopy(hdr,0,all,0,9); System.arraycopy(f.payload(),0,all,9,len);
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(' ').append(f.toString()).append(' ').append(FrameType.name(f.type())).append('\n');
        for (int off = 0; off < all.length; off += 16) {
            sb.append(String.format("%04x  ", off));
            for (int i = 0; i < 16; i++) { if (off+i < all.length) sb.append(String.format("%02x ", all[off+i])); else sb.append("   "); if (i==7) sb.append(' '); }
            sb.append(' ');
            for (int i = 0; i < 16 && off+i < all.length; i++) { int b = all[off+i]&0xFF; sb.append((b>=32&&b<127)?(char)b:'.'); }
            sb.append('\n');
        }
        sb.append("  Length=").append(len).append(" Type=0x").append(String.format("%02X",f.type())).append(' ').append(FrameType.name(f.type())).append(" Flags=0x").append(String.format("%02X",f.flags())).append(" ID=").append(f.requestId()).append('\n');
        return sb.toString();
    }
}
