package appeng.crafting.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ContainerPatternAccessorTest {
    @Test
    void exposesInheritedPatternPreviewFieldsThroughAccessorMethods() throws Exception {
        var methods = new java.util.HashMap<String, String>();
        try (InputStream classFile = getClass().getClassLoader()
                .getResourceAsStream("appeng/mixins/extendedae/ContainerPatternAccessor.class")) {
            assertThat(classFile).isNotNull();

            new ClassReader(classFile).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                        String[] exceptions) {
                    methods.put(name, descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE);
        }

        assertThat(methods).containsEntry("ae2$getPatternStack", "()Lnet/minecraft/world/item/ItemStack;");
        assertThat(methods).containsEntry("ae2$getInputs", "()Ljava/util/List;");
    }
}
