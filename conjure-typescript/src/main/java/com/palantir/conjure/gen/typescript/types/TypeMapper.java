/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.gen.typescript.types;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.palantir.conjure.defs.types.BaseObjectTypeDefinition;
import com.palantir.conjure.defs.types.ConjureType;
import com.palantir.conjure.defs.types.ConjureTypeVisitor;
import com.palantir.conjure.defs.types.ThrowingConjureTypeVisitor;
import com.palantir.conjure.defs.types.TypesDefinition;
import com.palantir.conjure.defs.types.builtin.AnyType;
import com.palantir.conjure.defs.types.builtin.BinaryType;
import com.palantir.conjure.defs.types.builtin.DateTimeType;
import com.palantir.conjure.defs.types.collect.ListType;
import com.palantir.conjure.defs.types.collect.MapType;
import com.palantir.conjure.defs.types.collect.OptionalType;
import com.palantir.conjure.defs.types.collect.SetType;
import com.palantir.conjure.defs.types.complex.EnumTypeDefinition;
import com.palantir.conjure.defs.types.complex.ObjectTypeDefinition;
import com.palantir.conjure.defs.types.complex.UnionTypeDefinition;
import com.palantir.conjure.defs.types.names.ConjurePackage;
import com.palantir.conjure.defs.types.names.ConjurePackages;
import com.palantir.conjure.defs.types.primitive.PrimitiveType;
import com.palantir.conjure.defs.types.reference.AliasTypeDefinition;
import com.palantir.conjure.defs.types.reference.ExternalTypeDefinition;
import com.palantir.conjure.defs.types.reference.ForeignReferenceType;
import com.palantir.conjure.defs.types.reference.ImportedTypes;
import com.palantir.conjure.defs.types.reference.LocalReferenceType;
import com.palantir.conjure.defs.types.reference.ReferenceType;
import com.palantir.conjure.gen.typescript.poet.TypescriptSimpleType;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

public final class TypeMapper {

    private final Optional<ConjurePackage> defaultPackage;
    private final TypesDefinition types;

    public TypeMapper(TypesDefinition types, Optional<ConjurePackage> defaultPackage) {
        this.types = types;
        this.defaultPackage = defaultPackage;
    }

    public TypescriptSimpleType getTypescriptType(ConjureType conjureType) {
        return TypescriptSimpleType.of(getTypeNameFromConjureType(conjureType));
    }

    public Set<ReferenceType> getReferencedConjureNames(ConjureType conjureType) {
        ImmutableSet.Builder<ReferenceType> result = ImmutableSet.builder();
        Stack<ConjureType> stack = new Stack<>();
        ReferencedNameVisitor visitor = new ReferencedNameVisitor(stack, result);
        stack.add(conjureType);
        while (!stack.isEmpty()) {
            ConjureType poppedConjureType = stack.pop();
            poppedConjureType.visit(visitor);
        }
        return result.build();
    }

    public Optional<ConjurePackage> getContainingPackage(ReferenceType referenceType) {
        return referenceType.visit(new ContainingPackageVisitor());
    }

    private static class ReferencedNameVisitor implements ConjureTypeVisitor<Void> {

        private final Stack<ConjureType> stack;
        private final Builder<ReferenceType> result;

        ReferencedNameVisitor(Stack<ConjureType> stack, ImmutableSet.Builder<ReferenceType> result) {
            this.stack = stack;
            this.result = result;
        }

        @Override
        public Void visitAny(AnyType type) {
            return null;
        }

        @Override
        public Void visitList(ListType type) {
            stack.add(type.itemType());
            return null;
        }

        @Override
        public Void visitMap(MapType type) {
            stack.add(type.keyType());
            stack.add(type.valueType());
            return null;
        }

        @Override
        public Void visitOptional(OptionalType type) {
            stack.add(type.itemType());
            return null;
        }

        @Override
        public Void visitPrimitive(PrimitiveType type) {
            return null;
        }

        @Override
        public Void visitLocalReference(LocalReferenceType type) {
            result.add(type);
            return null;
        }

        @Override
        public Void visitForeignReference(ForeignReferenceType type) {
            result.add(type);
            return null;
        }

        @Override
        public Void visitSet(SetType type) {
            stack.add(type.itemType());
            return null;
        }

        @Override
        public Void visitBinary(BinaryType type) {
            return null;
        }

        @Override
        public Void visitDateTime(DateTimeType type) {
            return null;
        }

    }

    private String getTypeNameFromConjureType(ConjureType conjureType) {
        return conjureType.visit(new TypeNameVisitor());
    }


    private class ContainingPackageVisitor implements ThrowingConjureTypeVisitor<Optional<ConjurePackage>> {
        @Override
        public Optional<ConjurePackage> visitLocalReference(LocalReferenceType type) {
            BaseObjectTypeDefinition defType = types.definitions().objects().get(type.type());
            if (defType != null) {
                if (defType instanceof ObjectTypeDefinition || defType instanceof EnumTypeDefinition
                        || defType instanceof UnionTypeDefinition) {
                    return Optional.of(ConjurePackages.getPackage(
                            defType.conjurePackage(), defaultPackage, type.type()));
                } else if (!(defType instanceof AliasTypeDefinition)) {
                    throw new IllegalArgumentException("Unknown base object type definition");
                }
            }
            // TODO(rmcnamara): for now assume to generate primitive types for external definitions
            return Optional.empty();
        }

        @Override
        public Optional<ConjurePackage> visitForeignReference(ForeignReferenceType type) {
            return Optional.of(types.getImportsForRefNameSpace(type).getPackageForImportedType(type));
        }
    }

    private class TypeNameVisitor implements ConjureTypeVisitor<String> {

        @Override
        public String visitAny(AnyType type) {
            return "any";
        }

        @Override
        public String visitList(ListType type) {
            return getTypeNameFromConjureType(type.itemType()) + "[]";
        }

        @Override
        public String visitMap(MapType type) {
            String keyType = getTypeNameFromConjureType(type.keyType());
            String valueType = getTypeNameFromConjureType(type.valueType());
            return "{ [key: " + keyType + "]: " + valueType + " }";
        }

        @Override
        public String visitOptional(OptionalType type) {
            return getTypeNameFromConjureType(type.itemType()) + " | null";
        }

        @Override
        public String visitPrimitive(PrimitiveType type) {
            switch (type) {
                case DOUBLE:
                case INTEGER:
                case SAFELONG:
                    return "number";
                case STRING:
                case RID:
                case BEARERTOKEN:
                    return "string";
                case BOOLEAN:
                    return "boolean";
                default:
                    throw new IllegalArgumentException("Unknown primitive type" + type);
            }
        }

        @Override
        public String visitLocalReference(LocalReferenceType type) {
            BaseObjectTypeDefinition defType = types.definitions().objects().get(type.type());
            if (defType != null) {
                return extractTypescriptName(type, defType);
            } else {
                ExternalTypeDefinition depType = types.imports().get(type.type());
                checkNotNull(depType, "Unable to resolve type %s", type.type());
                return getTypeNameFromConjureType(depType.baseType());
            }
        }

        @Override
        public String visitForeignReference(ForeignReferenceType type) {
            ImportedTypes importedTypes = types.conjureImports().get(type.namespace());
            if (importedTypes != null) {
                BaseObjectTypeDefinition defType = importedTypes.importedTypes().objects().get(type.type());
                checkNotNull(defType, "Unable to resolve imported conjure type %s", type.type());
                return extractTypescriptName(type, defType);
            } else {
                ExternalTypeDefinition depType = types.imports().get(type.type());
                checkNotNull(depType, "Unable to resolve type %s", type.type());
                return getTypeNameFromConjureType(depType.baseType());
            }
        }

        @Override
        public String visitSet(SetType type) {
            return getTypeNameFromConjureType(type.itemType()) + "[]";
        }

        @Override
        public String visitBinary(BinaryType type) {
            return "any";
        }

        @Override
        public String visitDateTime(DateTimeType type) {
            return "string";
        }

        private String extractTypescriptName(ReferenceType referenceType, BaseObjectTypeDefinition defType) {
            if (defType instanceof AliasTypeDefinition) {
                // in typescript we collapse alias types to concrete types
                return getAliasedType(referenceType, (AliasTypeDefinition) defType).visit(this);
            } else if (defType instanceof EnumTypeDefinition) {
                return referenceType.type().name();
            } else {
                // Interfaces are prepended with "I"
                return "I" + referenceType.type().name();
            }
        }

        // If the original ReferenceType is a ForeignReferenceType and the aliased type is a LocalReferenceType,
        // then the LocalReferenceType has to be converted to a ForeignReferenceType for resolution to work
        private ConjureType getAliasedType(ReferenceType referenceType, AliasTypeDefinition aliasTypeDefinition) {
            ConjureType aliasedType = aliasTypeDefinition.alias();
            if (referenceType instanceof ForeignReferenceType
                    && aliasedType instanceof LocalReferenceType
                    && !(aliasedType instanceof PrimitiveType)) {

                LocalReferenceType localAliasedType = (LocalReferenceType) aliasedType;
                return ForeignReferenceType.of(
                        ((ForeignReferenceType) referenceType).namespace(),
                        localAliasedType.type());
            }
            return aliasedType;
        }
    }
}
