package djinni

import djinni.ast._
import djinni.generatorTools._
import djinni.meta._

class JNIMarshal(spec: Spec) extends Marshal(spec) {

  // For JNI typename() is always fully qualified and describes the mangled Java type to be used in field/method signatures
  override def typename(tm: MExpr): String = javaTypeSignature(tm)
  def typename(name: String, ty: TypeDef): String = throw new AssertionError("not supported")

  override def fqTypename(tm: MExpr): String = typename(tm)
  def fqTypename(name: String, ty: TypeDef): String = typename(name, ty)

  // Name for the autogenerated class containing field/method IDs and toJava()/fromJava() methods
  def helperClass(name: String) = spec.jniClassIdentStyle(name)
  def fqHelperClass(name: String) = withNs(Some(spec.jniNamespace), helperClass(name))

  def toJniType(ty: TypeRef): String = toJniType(ty.resolved, false)
  def toJniType(m: MExpr, needRef: Boolean): String = m.base match {
    case p: MPrimitive => if (needRef) "jobject" else p.jniName
    case MString => "jstring"
    case MOptional => toJniType(m.args.head, true)
    case MBinary => "jbyteArray"
    case tp: MParam => helperClass(tp.name) + "::JniType"
    case _ => "jobject"
  }

  // The mangled Java typename without the "L...;" decoration useful only for class reflection on our own type
  def undecoratedTypename(name: String, ty: TypeDef): String = {
    val javaClassName = idJava.ty(name)
    spec.javaPackage.fold(javaClassName)(p => p.replaceAllLiterally(".", "/") + "/" + javaClassName)
  }

  private def javaTypeSignature(tm: MExpr): String = tm.base match {
    case o: MOpaque => o match {
      case p: MPrimitive => p.jSig
      case MString => "Ljava/lang/String;"
      case MDate => "Ljava/util/Date;"
      case MBinary => "[B"
      case MOptional =>  tm.args.head.base match {
        case p: MPrimitive => s"Ljava/lang/${p.jBoxed};"
        case MOptional => throw new AssertionError("nested optional?")
        case m => javaTypeSignature(tm.args.head)
      }
      case MList => "Ljava/util/ArrayList;"
      case MSet => "Ljava/util/HashSet;"
      case MMap => "Ljava/util/HashMap;"
    }
    case MParam(_) => "Ljava/lang/Object;"
    case d: MDef => s"L${undecoratedTypename(d.name, d.body)};"
  }

}
