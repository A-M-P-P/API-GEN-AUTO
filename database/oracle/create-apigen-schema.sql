-- À exécuter par le DBA avec SQL*Plus/SQLcl dans la PDB/service cible.
-- Ne pas exécuter ce script depuis Flyway ni avec le compte d'un système métier.
-- Tablespaces USERS et TEMP à adapter par le DBA si nécessaire.
-- Un compte APIGEN déjà présent provoque volontairement un arrêt, sans modification.
SET ECHO OFF
SET VERIFY OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE
-- Mot de passe initial demandé : à remplacer pour la production.
CREATE USER APIGEN IDENTIFIED BY "APIGEN1234"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP
    QUOTA 1G ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO APIGEN;
PROMPT Compte APIGEN cree. Configurer le backend puis laisser Flyway creer ses tables.
PROMPT Aucun droit sur les schemas metier n'a ete accorde.
EXIT SUCCESS
