package com.aadventure.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class WorldView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Obj> objects = new ArrayList<>();
    private float camX=0, camY=0, lastX, lastY;
    private boolean drag;

    static class Obj { float x,y,s; int type; Obj(float x,float y,float s,int type){this.x=x;this.y=y;this.s=s;this.type=type;} }

    public WorldView(Context c) {
        super(c);
        // A simple large world: freely pannable from one side to the other.
        for(int i=0;i<28;i++){
            float x=-1800 + (i*347)%3600, y=-1200 + (i*521)%2400;
            objects.add(new Obj(x,y,0.8f+(i%4)*0.12f,0));
        }
        for(int i=0;i<34;i++){
            float x=-1700 + (i*233)%3400, y=-1100 + (i*419)%2200;
            objects.add(new Obj(x,y,0.7f+(i%3)*0.15f,1));
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float scale = Math.min(getWidth()/1800f, getHeight()/1100f);
        scale = Math.max(scale, 0.35f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(105,170,75));
        c.drawColor(Color.rgb(125,190,88));

        c.save();
        c.translate(getWidth()/2f, getHeight()/2f);
        c.scale(scale,scale);
        c.translate(-camX,-camY);

        // World boundary
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(112,178,78));
        c.drawRect(-1900,-1300,1900,1300,p);

        for(Obj o: objects){
            if(o.type==0) drawTree(c,o);
            else drawRock(c,o);
        }
        c.restore();

        p.setColor(Color.WHITE); p.setTextSize(34); p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("A Adventure 0.1.1v", 28, 46, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(22);
        c.drawText("Drag the screen to explore the whole map", 28, 78, p);
    }

    private void drawTree(Canvas c, Obj o){
        p.setColor(Color.rgb(105,72,42)); c.drawRect(o.x-14*o.s,o.y,o.x+14*o.s,o.y+90*o.s,p);
        p.setColor(Color.rgb(45,120,48));
        c.drawCircle(o.x,o.y-30*o.s,58*o.s,p);
        c.drawCircle(o.x-38*o.s,o.y-5*o.s,45*o.s,p);
        c.drawCircle(o.x+38*o.s,o.y-5*o.s,45*o.s,p);
    }

    private void drawRock(Canvas c, Obj o){
        p.setColor(Color.rgb(105,105,100));
        Path path=new Path();
        path.moveTo(o.x-45*o.s,o.y+20*o.s); path.lineTo(o.x-28*o.s,o.y-22*o.s);
        path.lineTo(o.x+12*o.s,o.y-38*o.s); path.lineTo(o.x+48*o.s,o.y-5*o.s);
        path.lineTo(o.x+28*o.s,o.y+28*o.s); path.close();
        c.drawPath(path,p);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN: lastX=e.getX(); lastY=e.getY(); drag=true; return true;
            case MotionEvent.ACTION_MOVE:
                float dx=e.getX()-lastX, dy=e.getY()-lastY;
                float scale=Math.max(0.35f,Math.min(getWidth()/1800f,getHeight()/1100f));
                camX-=dx/scale; camY-=dy/scale;
                camX=Math.max(-900,Math.min(900,camX));
                camY=Math.max(-650,Math.min(650,camY));
                lastX=e.getX(); lastY=e.getY(); invalidate(); return true;
            case MotionEvent.ACTION_UP: drag=false; return true;
        }
        return true;
    }
}
