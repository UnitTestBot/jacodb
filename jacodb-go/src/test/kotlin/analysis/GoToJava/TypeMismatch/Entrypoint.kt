/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package analysis.GoToJava.TypeMismatch

import java.io.BufferedReader

val ptrMap: MutableMap<Int, Any> = mutableMapOf()
val structToPtrMap: MutableMap<Any, Int> = mutableMapOf()
val ptrToJacoMap: MutableMap<Int, Any> = mutableMapOf()
val mapDec: Map<String, (BufferedReader, Int)->Any?> = mapOf(
    "Int" to ::readInteger,
    "Short" to ::readInteger,
    "Long" to ::readInteger,
	"ULong" to ::readULong,
    "Float" to ::readReal,
    "Double" to ::readReal,
    "String" to ::readString,
    "Boolean" to ::readBoolean,
    "nil" to ::readNil,

    "array" to ::readArray,
    "map" to ::readMap,
	"types_Initializer" to ::read_types_Initializer,
	"ast_FuncType" to ::read_ast_FuncType,
	"types_instance" to ::read_types_instance,
	"typeutil_Map" to ::read_typeutil_Map,
	"ast_ExprStmt" to ::read_ast_ExprStmt,
	"ssa_Return" to ::read_ssa_Return,
	"types_Tuple" to ::read_types_Tuple,
	"typeutil_entry" to ::read_typeutil_entry,
	"ast_AssignStmt" to ::read_ast_AssignStmt,
	"sync_Mutex" to ::read_sync_Mutex,
	"ast_TypeSpec" to ::read_ast_TypeSpec,
	"types_term" to ::read_types_term,
	"types_exprInfo" to ::read_types_exprInfo,
	"ssa_subster" to ::read_ssa_subster,
	"atomic_Int32" to ::read_atomic_Int32,
	"types_TypeParam" to ::read_types_TypeParam,
	"types_TypeParamList" to ::read_types_TypeParamList,
	"types_TypeList" to ::read_types_TypeList,
	"types_instanceLookup" to ::read_types_instanceLookup,
	"types_monoVertex" to ::read_types_monoVertex,
	"types_environment" to ::read_types_environment,
	"generatedInlineStruct_000" to ::read_generatedInlineStruct_000,
	"types_Info" to ::read_types_Info,
	"ast_BasicLit" to ::read_ast_BasicLit,
	"types_MethodSet" to ::read_types_MethodSet,
	"token_File" to ::read_token_File,
	"ssa_Package" to ::read_ssa_Package,
	"types_monoEdge" to ::read_types_monoEdge,
	"ssa_Alloc" to ::read_ssa_Alloc,
	"ssa_typeListMap" to ::read_ssa_typeListMap,
	"types_Nil" to ::read_types_Nil,
	"ssa_BinOp" to ::read_ssa_BinOp,
	"ssa_lblock" to ::read_ssa_lblock,
	"ast_IncDecStmt" to ::read_ast_IncDecStmt,
	"types_Package" to ::read_types_Package,
	"ast_Object" to ::read_ast_Object,
	"ast_CommentGroup" to ::read_ast_CommentGroup,
	"ast_Field" to ::read_ast_Field,
	"sync_Once" to ::read_sync_Once,
	"ssa_tpWalker" to ::read_ssa_tpWalker,
	"ast_BlockStmt" to ::read_ast_BlockStmt,
	"ssa_Const" to ::read_ssa_Const,
	"types_Checker" to ::read_types_Checker,
	"types_PkgName" to ::read_types_PkgName,
	"ast_BinaryExpr" to ::read_ast_BinaryExpr,
	"ssa_Call" to ::read_ssa_Call,
	"ssa_If" to ::read_ssa_If,
	"ssa_Store" to ::read_ssa_Store,
	"ssa_generic" to ::read_ssa_generic,
	"ssa_targets" to ::read_ssa_targets,
	"types_Selection" to ::read_types_Selection,
	"ssa_Global" to ::read_ssa_Global,
	"ssa_UnOp" to ::read_ssa_UnOp,
	"token_lineInfo" to ::read_token_lineInfo,
	"ast_Ident" to ::read_ast_Ident,
	"ast_Comment" to ::read_ast_Comment,
	"types_actionDesc" to ::read_types_actionDesc,
	"ssa_Parameter" to ::read_ssa_Parameter,
	"types_Scope" to ::read_types_Scope,
	"types_Context" to ::read_types_Context,
	"types_Term" to ::read_types_Term,
	"generatedInlineStruct_001" to ::read_generatedInlineStruct_001,
	"types_importKey" to ::read_types_importKey,
	"ast_File" to ::read_ast_File,
	"types_Var" to ::read_types_Var,
	"ast_FieldList" to ::read_ast_FieldList,
	"ast_Scope" to ::read_ast_Scope,
	"ssa_Jump" to ::read_ssa_Jump,
	"ssa_register" to ::read_ssa_register,
	"types_Pointer" to ::read_types_Pointer,
	"types_ctxtEntry" to ::read_types_ctxtEntry,
	"ssa_BasicBlock" to ::read_ssa_BasicBlock,
	"ssa_Function" to ::read_ssa_Function,
	"types_Signature" to ::read_types_Signature,
	"types_Label" to ::read_types_Label,
	"types_Builtin" to ::read_types_Builtin,
	"types_Interface" to ::read_types_Interface,
	"ssa_Program" to ::read_ssa_Program,
	"ssa_selection" to ::read_ssa_selection,
	"types_Config" to ::read_types_Config,
	"ast_SelectorExpr" to ::read_ast_SelectorExpr,
	"ast_FuncDecl" to ::read_ast_FuncDecl,
	"typeutil_Hasher" to ::read_typeutil_Hasher,
	"token_FileSet" to ::read_token_FileSet,
	"types_Union" to ::read_types_Union,
	"types_action" to ::read_types_action,
	"types_TypeAndValue" to ::read_types_TypeAndValue,
	"types_declInfo" to ::read_types_declInfo,
	"types_object" to ::read_types_object,
	"types_dotImportKey" to ::read_types_dotImportKey,
	"ssa_FreeVar" to ::read_ssa_FreeVar,
	"ast_ReturnStmt" to ::read_ast_ReturnStmt,
	"ast_ImportSpec" to ::read_ast_ImportSpec,
	"types__TypeSet" to ::read_types__TypeSet,
	"types_monoGraph" to ::read_types_monoGraph,
	"types_Basic" to ::read_types_Basic,
	"ssa_canonizer" to ::read_ssa_canonizer,
	"types_version" to ::read_types_version,
	"types_Named" to ::read_types_Named,
	"types_TypeName" to ::read_types_TypeName,
	"types_Const" to ::read_types_Const,
	"ssa_CallCommon" to ::read_ssa_CallCommon,
	"sync_RWMutex" to ::read_sync_RWMutex,
	"types_Func" to ::read_types_Func,
	"types_Instance" to ::read_types_Instance,
	"ast_CallExpr" to ::read_ast_CallExpr,
	"ssa_domInfo" to ::read_ssa_domInfo,
	"typeutil_MethodSetCache" to ::read_typeutil_MethodSetCache
)

fun StartDeserializer(buffReader: BufferedReader): Any? {
    val line = buffReader.readLine()
    val split = line.split(" ")
    val readType = split[0]
    var id = -1
    if (split.size > 1) {
        id = split[1].toInt()
    }
    return mapDec[readType]?.invoke(buffReader, id)
}
