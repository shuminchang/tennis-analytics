package com.shuminapp.tennis.visualization;

import java.util.*;
import static com.shuminapp.tennis.visualization.ResearchDtos.*;

public final class ResearchStatistics {
    private ResearchStatistics() { }
    public static Double pearson(double[] x,double[] y) {
        if(x.length!=y.length) throw new IllegalArgumentException();if(x.length<3) return null;
        double mx=Arrays.stream(x).average().orElseThrow(),my=Arrays.stream(y).average().orElseThrow(),xx=0,yy=0,xy=0;
        for(int i=0;i<x.length;i++) { double a=x[i]-mx,b=y[i]-my;xx+=a*a;yy+=b*b;xy+=a*b; }
        return xx==0 || yy==0?null:Math.max(-1,Math.min(1,xy/Math.sqrt(xx*yy)));
    }
    public static double[] ranks(double[] input) {
        Integer[] index=new Integer[input.length];for(int i=0;i<index.length;i++) index[i]=i;
        Arrays.sort(index,Comparator.comparingDouble(i->input[i]));double[] out=new double[input.length];
        for(int start=0;start<index.length;) { int end=start+1;while(end<index.length && Double.compare(input[index[start]],input[index[end]])==0) end++;
            double rank=(start+1+end)/2.0;for(int k=start;k<end;k++) out[index[k]]=rank;start=end;
        }
        return out;
    }
    public static Double spearman(double[] x,double[] y) { return pearson(ranks(x),ranks(y)); }
    public static double quantile(double[] sorted,double p) {
        if(sorted.length==0 || p<0 || p>1) throw new IllegalArgumentException();double index=(sorted.length-1)*p;int low=(int)Math.floor(index),high=(int)Math.ceil(index);return sorted[low]+(sorted[high]-sorted[low])*(index-low);
    }
    public static Box box(String metric,double[] values) {
        if(values.length==0) return new Box(metric,0,null,null,null,null,null,List.of());
        double[] a=values.clone();Arrays.sort(a);double q1=quantile(a,.25),median=quantile(a,.5),q3=quantile(a,.75),iqr=q3-q1;
        double lower=Arrays.stream(a).filter(v->v>=q1-1.5*iqr).min().orElse(a[0]),upper=Arrays.stream(a).filter(v->v<=q3+1.5*iqr).max().orElse(a[a.length-1]);
        return new Box(metric,a.length,lower,q1,median,q3,upper,Arrays.stream(a).filter(v->v<lower || v>upper).boxed().toList());
    }
    public static Hexes hexes(double[] x,double[] y) {
        if(x.length!=y.length) throw new IllegalArgumentException();if(x.length==0) return new Hexes(0,null,null,List.of());
        double xmin=Arrays.stream(x).min().orElseThrow(),xmax=Arrays.stream(x).max().orElseThrow(),ymin=Arrays.stream(y).min().orElseThrow(),ymax=Arrays.stream(y).max().orElseThrow();
        if(xmin==xmax) { xmin-=.5;xmax+=.5; }if(ymin==ymax) { ymin-=.5;ymax+=.5; }
        double size=.06;Map<String,long[]> cells=new TreeMap<>();
        for(int i=0;i<x.length;i++) {
            double nx=(x[i]-xmin)/(xmax-xmin),ny=(y[i]-ymin)/(ymax-ymin),q=(Math.sqrt(3)/3*nx-ny/3)/size,r=(2*ny/3)/size;
            long cq=Math.round(q),cr=Math.round(r),cy=Math.round(-q-r);double dq=Math.abs(cq-q),dr=Math.abs(cr-r),dy=Math.abs(cy+q+r);
            if(dq>dr && dq>dy) cq=-cy-cr;else if(dr>dy) cr=-cq-cy;
            long fq=cq,fr=cr;cells.computeIfAbsent(cq+":"+cr,k->new long[]{fq,fr,0})[2]++;
        }
        List<HexCell> out=new ArrayList<>();
        for(var cell:cells.values()) {
            double nx=size*Math.sqrt(3)*(cell[0]+cell[1]/2.0),ny=size*1.5*cell[1];var vertices=new ArrayList<List<Double>>();
            for(int k=0;k<6;k++) { double angle=Math.toRadians(30+60*k);vertices.add(List.of(xmin+(nx+size*Math.cos(angle))*(xmax-xmin),ymin+(ny+size*Math.sin(angle))*(ymax-ymin))); }
            out.add(new HexCell(xmin+nx*(xmax-xmin),ymin+ny*(ymax-ymin),cell[2],List.copyOf(vertices)));
        }
        return new Hexes(x.length,new VisualizationDtos.Domain(xmin,xmax),new VisualizationDtos.Domain(ymin,ymax),List.copyOf(out));
    }
}
