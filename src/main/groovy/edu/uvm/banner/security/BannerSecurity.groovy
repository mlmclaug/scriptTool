package edu.uvm.banner.security;
import groovy.sql.Sql;

final class BannerSecurity extends BannerSeeds{
  // Banner Security for Object... do I have permission to execute this object?
  private static String setBanSecr = '''
declare
  hold_cmd  varchar2(240);
  object    varchar2(30);
  version   varchar2(10);
  password  varchar2(30);
  role_name varchar2(30);
  password_out  varchar2(30);
  seed1     number(8) := :seed1;
  seed3     number(8) := :seed3;

begin
  object     :=  :sql_setrole_object;   
  version    :=  '1.0';

  G$_SECURITY.G$_VERIFY_PASSWORD1_PRD(object, version,
                                      password, role_name);
  IF PASSWORD = 'INSECURED' THEN
      RETURN;
  END IF;

  password_out :=G$_SECURITY.G$_DECRYPT_FNC(PASSWORD, seed3);

  G$_SECURITY.G$_VERIFY_PASSWORD1_PRD(object, version,
                                      password_out, role_name);
  password_out := G$_SECURITY.G$_DECRYPT_FNC(password_out, seed1);

  PASSWORD := '"' || PASSWORD_OUT  || '"';
  HOLD_CMD := ROLE_NAME || ' IDENTIFIED BY ' || PASSWORD;
  PASSWORD := ' '; seed1    := 0; seed3    := 0;

  DBMS_SESSION.SET_ROLE(HOLD_CMD);
end;
''' 

static void apply( def script, Sql dbsession){
  String scriptnm = script.getScriptName().toUpperCase()
  //script.sql.call(setBanSecr,[seed1, seed3, scriptnm])
  dbsession.call(setBanSecr,[seed1, seed3, scriptnm])
}
}