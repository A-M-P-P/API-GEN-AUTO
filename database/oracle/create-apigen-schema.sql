-- À exécuter par le DBA avec SQL*Plus/SQLcl dans la PDB/service cible.
-- Ne pas exécuter ce script depuis Flyway ni avec le compte d'un système métier.
-- Tablespaces USERS et TEMP à adapter par le DBA si nécessaire.
-- Un compte APIGEN déjà présent provoque volontairement un arrêt, sans modification.
SET ECHO OFF
SET VERIFY OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE
ACCEPT apigen_password CHAR PROMPT 'Mot de passe du nouveau compte APIGEN (sans guillemet double ni &): ' HIDE
CREATE USER APIGEN IDENTIFIED BY "&apigen_password"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP
    QUOTA 1G ON USERS;
UNDEFINE apigen_password
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO APIGEN;
PROMPT Compte APIGEN cree. Configurer le backend puis laisser Flyway creer ses tables.
PROMPT Aucun droit sur les schemas metier n'a ete accorde.
EXIT SUCCESS
