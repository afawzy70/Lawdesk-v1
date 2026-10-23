-- QAD-008: V1 only trimmed ASCII spaces, so a version of tabs, line breaks or Unicode spaces was accepted.
-- A version must contain at least one character that is not Unicode White_Space, a zero-width space or a BOM;
-- AppRelease applies the same rule. Each character is listed (no ranges: bracket ranges depend on the collation).
-- Existing rows that break the rule make this migration fail, and the whole migration rolls back.
alter table app_release drop constraint app_release_version_check;
alter table app_release add constraint app_release_version_visible
    check (version ~ '[^ \t\n\v\f\r\u0085\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u200b\u2028\u2029\u202f\u205f\u3000\ufeff]');
