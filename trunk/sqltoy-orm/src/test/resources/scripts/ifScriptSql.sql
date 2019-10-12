select * 
from sqltoy_demo t
where #[@if(:id<>null)t.id=:id ]
      #[@if(:id==null)or (t.name=:name and t.status=:status)]