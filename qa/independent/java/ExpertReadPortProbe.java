package com.lawdesk.experts;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

/** Independent QA probe of the actual candidate class and actual disposable PostgreSQL, not developer tests. */
public final class ExpertReadPortProbe {
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static void check(String id, String expected, boolean ok, Object actual) {
        results.add(Map.of("id",id,"expected",expected,"ok",ok,"actual",actual));
    }
    public static void main(String[] args) throws Exception {
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:"+Integer.parseInt(args[0])+"/postgres","lawdesk_qa","");
        var port=new ExpertReportQueries(new NamedParameterJdbcTemplate(ds));
        UUID active=UUID.fromString(args[1]),inactive=UUID.fromString(args[2]),unknown=UUID.randomUUID();
        check("EMPTY","Empty input returns empty map",port.experts(List.of()).isEmpty(),port.experts(List.of()).size());
        var found=port.experts(List.of(active,inactive,unknown));
        check("KNOWN-UNKNOWN","Only requested existing IDs returned",found.keySet().equals(Set.of(active,inactive)),found.size());
        var a=found.get(active);var b=found.get(inactive);
        check("IDENTITY","Exact bilingual names, specialties and active flags",a!=null&&b!=null&&a.name().equals("QA خبير %_ ' literal")&&a.specialty().equals("QA Engineering")&&a.active()&&b.name().equals("QA inactive expert")&&!b.active(),Map.of("active_found",a!=null,"inactive_found",b!=null));
        check("DUPLICATES","Duplicate IDs do not duplicate entries",port.experts(List.of(active,active,inactive)).size()==2,port.experts(List.of(active,active,inactive)).size());
        var fields=Arrays.stream(ExpertReportQueries.ExpertRef.class.getRecordComponents()).map(x->x.getName()).collect(java.util.stream.Collectors.toSet());
        check("PROJECTION","Only id/name/specialty/active; no office or phone",fields.equals(Set.of("id","name","specialty","active")),fields);
        var ids=new ArrayList<UUID>();ids.add(active);ids.add(inactive);while(ids.size()<10000)ids.add(UUID.randomUUID());
        var max=port.experts(ids);check("LIMIT-10000","10000 IDs accepted; exactly2 seeded entries returned",max.keySet().equals(Set.of(active,inactive)),max.size());
        ids.add(UUID.randomUUID());boolean rejected=false;try{port.experts(ids);}catch(IllegalArgumentException e){rejected=true;}
        check("LIMIT-10001","10001 IDs rejected",rejected,rejected);
        check("AFTER-REFUSAL","Valid reads still succeed after rejected call",port.experts(List.of(active)).containsKey(active),port.experts(List.of(active)).size());
        System.out.println(new ObjectMapper().writeValueAsString(results));
    }
}
