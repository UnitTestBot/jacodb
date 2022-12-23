DROP TABLE IF EXISTS "BytecodeLocations" CASCADE;
DROP TABLE IF EXISTS "Symbols"  CASCADE;
DROP TABLE IF EXISTS "OuterClasses" CASCADE;
DROP TABLE IF EXISTS "Methods" CASCADE;
DROP TABLE IF EXISTS "Classes" CASCADE;
DROP TABLE IF EXISTS "ClassHierarchies" CASCADE;
DROP TABLE IF EXISTS "ClassInnerClasses" CASCADE;
DROP TABLE IF EXISTS "MethodParameters" CASCADE;
DROP TABLE IF EXISTS "Fields" CASCADE;
DROP TABLE IF EXISTS "Annotations" CASCADE;
DROP TABLE IF EXISTS "AnnotationValues" CASCADE;
DROP INDEX IF EXISTS "Symbols_name";
DROP INDEX IF EXISTS "Classes_name";
DROP INDEX IF EXISTS "Methods_class_id_name_desc";
DROP INDEX IF EXISTS "Fields_class_id_name";
DROP INDEX IF EXISTS "Class Hierarchies";
