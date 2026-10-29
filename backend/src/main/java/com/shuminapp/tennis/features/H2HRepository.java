package com.shuminapp.tennis.features;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class H2HRepository {
    private final JdbcTemplate jdbc;
    public H2HRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Result between(String tour,int first,int second,LocalDate from,LocalDate to,String surface) {
        List<Object> args=new ArrayList<>(List.of(tour,first,second,second,first));
        String where=" FROM v_stat_matches WHERE tour=? AND ((winner_id=? AND loser_id=?) OR (winner_id=? AND loser_id=?))";
        if(from!=null) { where+=" AND tourney_date>=?";args.add(from); }if(to!=null) { where+=" AND tourney_date<=?";args.add(to); }
        if(surface!=null) { where+=" AND surface=?";args.add(surface); }
        List<Object> queryArgs=new ArrayList<>(args);queryArgs.addFirst(first);
        long[] counts=jdbc.queryForObject("SELECT count(*),count(*) FILTER(WHERE winner_id=?)"+where,(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},queryArgs.toArray());
        return new Result("stats-v1",counts[0],counts[1],counts[0]-counts[1],counts[0]==0?null:(double)counts[1]/counts[0],wilson(counts[1],counts[0]),groups(where,args,first,"coalesce(surface,'Unknown')"),groups(where,args,first,"extract(year from tourney_date)::integer::text"));
    }
    private List<Group> groups(String where,List<Object> args,int first,String expression) {
        var values=new ArrayList<>(args);values.addFirst(first);
        return jdbc.query("SELECT "+expression+" AS bucket,count(*),count(*) FILTER(WHERE winner_id=?)"+where+" GROUP BY bucket ORDER BY bucket",(rs,n)->new Group(rs.getString(1),rs.getLong(2),rs.getLong(3),rs.getLong(2)-rs.getLong(3)),values.toArray());
    }
    public static Interval wilson(long wins,long total) {
        if(total==0) return new Interval(null,null);double z=1.959963984540054,p=(double)wins/total,d=1+z*z/total,center=(p+z*z/(2*total))/d,half=z*Math.sqrt(p*(1-p)/total+z*z/(4d*total*total))/d;
        return new Interval(Math.max(0,center-half),Math.min(1,center+half));
    }
    public record Interval(Double lower,Double upper) { }
    public record Group(String bucket,long matches,long firstWins,long secondWins) { }
    public record Result(String ruleVersion,long matches,long firstWins,long secondWins,Double firstWinRate,Interval confidence95,List<Group> surfaces,List<Group> years) { }
}
