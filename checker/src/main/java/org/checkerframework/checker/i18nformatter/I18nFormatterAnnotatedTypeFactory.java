package org.checkerframework.checker.i18nformatter;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.i18nformatter.qual.I18nConversionCategory;
import org.checkerframework.checker.i18nformatter.qual.I18nFormat;
import org.checkerframework.checker.i18nformatter.qual.I18nFormatBottom;
import org.checkerframework.checker.i18nformatter.qual.I18nFormatFor;
import org.checkerframework.checker.i18nformatter.qual.I18nInvalidFormat;
import org.checkerframework.checker.i18nformatter.qual.I18nUnknownFormat;
import org.checkerframework.checker.i18nformatter.util.I18nFormatUtil;
import org.checkerframework.checker.signature.qual.CanonicalName;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.MostlyNoElementQualifierHierarchy;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.util.QualifierKind;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TypeSystemError;
import org.plumelib.reflection.Signatures;

/**
 * Adds {@link I18nFormat} to the type of tree, if it is a {@code String} or {@code char} literal
 * that represents a satisfiable format. The annotation's value is set to be a list of appropriate
 * {@link I18nConversionCategory} values for every parameter of the format.
 *
 * <p>It also creates a map from the provided translation file if exists. This map will be used to
 * get the corresponding value of a key when {@link java.util.ResourceBundle#getString} method is
 * invoked.
 *
 * @checker_framework.manual #i18n-formatter-checker Internationalization Format String Checker
 */
public class I18nFormatterAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

  /** The @{@link I18nUnknownFormat} annotation. */
  protected final AnnotationMirror I18NUNKNOWNFORMAT =
      AnnotationBuilder.fromClass(elements, I18nUnknownFormat.class);

  /** The @{@link I18nFormatBottom} annotation. */
  protected final AnnotationMirror I18NFORMATBOTTOM =
      AnnotationBuilder.fromClass(elements, I18nFormatBottom.class);

  /** The fully-qualified name of {@link I18nFormat}. */
  protected static final @CanonicalName String I18NFORMAT_NAME =
      I18nFormat.class.getCanonicalName();

  /** The fully-qualified name of {@link I18nInvalidFormat}. */
  protected static final @CanonicalName String I18NINVALIDFORMAT_NAME =
      I18nInvalidFormat.class.getCanonicalName();

  /** The fully-qualified name of {@link I18nFormatFor}. */
  protected static final @CanonicalName String I18NFORMATFOR_NAME =
      I18nFormatFor.class.getCanonicalName();

  /** Map from a translation file key to its value in the file. */
  public final Map<String, String> translations = Collections.unmodifiableMap(buildLookup());

  /** Syntax tree utilities. */
  protected final I18nFormatterTreeUtil treeUtil = new I18nFormatterTreeUtil(checker);

  /** Create a new I18nFormatterAnnotatedTypeFactory. */
  @SuppressWarnings("this-escape")
  public I18nFormatterAnnotatedTypeFactory(BaseTypeChecker checker) {
    super(checker);

    this.postInit();
  }

  /**
   * Builds a map from a translation file key to its value in the file. Builds the map for all files
   * in the "-Apropfiles" command-line argument.
   *
   * <p>Called only once, during initialization.
   *
   * @return a map from a translation file key to its value in the file
   */
  private Map<String, String> buildLookup() {
    Map<String, String> result = new HashMap<>();

    if (checker.hasOption("propfiles")) {
      for (String propfile : checker.getStringsOption("propfiles", File.pathSeparator)) {
        Properties prop = new Properties();
        ClassLoader cl = this.getClass().getClassLoader();
        if (cl == null) {
          // The class loader is null if the system class loader was used.
          cl = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(propfile)) {
          if (in != null) {
            prop.load(in);
          } else {
            // If the classloader didn't manage to load the file, try whether a
            // FileInputStream works. For absolute paths this might help.
            try (InputStream fis = new FileInputStream(propfile)) {
              prop.load(fis);
            } catch (FileNotFoundException e) {
              System.err.println("Couldn't find the properties file: " + propfile);
              // report(null, "propertykeychecker.filenotfound", propfile);
              // return Collections.emptySet();
              continue;
            }
          }

          for (String key : prop.stringPropertyNames()) {
            result.put(key, prop.getProperty(key));
          }
        } catch (Exception e) {
          // TODO: is there a nicer way to report messages, that are not connected to
          // an AST node?  One cannot use `report`, because it needs a node.
          System.err.println(
              "Exception in PropertyKeyChecker.keysOfPropertyFile while processing "
                  + propfile
                  + ": "
                  + e);
          e.printStackTrace();
        }
      }
    }

    if (checker.hasOption("bundlenames")) {
      for (String bundleName : checker.getStringsOption("bundlenames", ':')) {
        if (!Signatures.isBinaryName(bundleName)) {
          System.err.println(
              "Malformed resource bundle: <" + bundleName + "> should be a binary name.");
          continue;
        }
        ResourceBundle bundle = ResourceBundle.getBundle(bundleName);
        if (bundle == null) {
          System.err.println(
              "Couldn't find the resource bundle: <"
                  + bundleName
                  + "> for locale <"
                  + Locale.getDefault()
                  + ">.");
          continue;
        }

        for (String key : bundle.keySet()) {
          result.put(key, bundle.getString(key));
        }
      }
    }

    return result;
  }

  @Override
  protected QualifierHierarchy createQualifierHierarchy() {
    return new I18nFormatterQualifierHierarchy();
  }

  @Override
  public TreeAnnotator createTreeAnnotator() {
    return new ListTreeAnnotator(super.createTreeAnnotator(), new I18nFormatterTreeAnnotator(this));
  }

  private class I18nFormatterTreeAnnotator extends TreeAnnotator {
    public I18nFormatterTreeAnnotator(AnnotatedTypeFactory atypeFactory) {
      super(atypeFactory);
    }

    @Override
    public Void visitLiteral(LiteralTree tree, AnnotatedTypeMirror type) {
      if (!type.hasPrimaryAnnotationInHierarchy(I18NUNKNOWNFORMAT)) {
        String format = null;
        if (tree.getKind() == Tree.Kind.STRING_LITERAL) {
          format = (String) tree.getValue();
        }
        if (format != null) {
          AnnotationMirror anno;
          try {
            I18nConversionCategory[] cs = I18nFormatUtil.formatParameterCategories(format);
            anno = I18nFormatterAnnotatedTypeFactory.this.treeUtil.categoriesToFormatAnnotation(cs);
          } catch (IllegalArgumentException e) {
            anno =
                I18nFormatterAnnotatedTypeFactory.this.treeUtil.exceptionToInvalidFormatAnnotation(
                    e);
          }
          type.addAnnotation(anno);
        }
      }

      return super.visitLiteral(tree, type);
    }
  }

  /** I18nFormatterQualifierHierarchy. */
  class I18nFormatterQualifierHierarchy extends MostlyNoElementQualifierHierarchy {

    /** Qualifier kind for the @{@link I18nFormat} annotation. */
    private final QualifierKind I18NFORMAT_KIND;

    /** Qualifier kind for the @{@link I18nFormatFor} annotation. */
    private final QualifierKind I18NFORMATFOR_KIND;

    /** Qualifier kind for the @{@link I18nInvalidFormat} annotation. */
    private final QualifierKind I18NINVALIDFORMAT_KIND;

    /** Creates I18nFormatterQualifierHierarchy. */
    public I18nFormatterQualifierHierarchy() {
      super(
          I18nFormatterAnnotatedTypeFactory.this.getSupportedTypeQualifiers(),
          elements,
          I18nFormatterAnnotatedTypeFactory.this);
      this.I18NFORMAT_KIND = this.getQualifierKind(I18NFORMAT_NAME);
      this.I18NFORMATFOR_KIND = this.getQualifierKind(I18NFORMATFOR_NAME);
      this.I18NINVALIDFORMAT_KIND = this.getQualifierKind(I18NINVALIDFORMAT_NAME);
    }

    @Override
    protected boolean isSubtypeWithElements(
        AnnotationMirror subAnno,
        QualifierKind subKind,
        AnnotationMirror superAnno,
        QualifierKind superKind) {
      if (subKind == I18NFORMAT_KIND && superKind == I18NFORMAT_KIND) {

        I18nConversionCategory[] rhsArgTypes = treeUtil.formatAnnotationToCategories(subAnno);
        I18nConversionCategory[] lhsArgTypes = treeUtil.formatAnnotationToCategories(superAnno);

        if (rhsArgTypes.length > lhsArgTypes.length) {
          return false;
        }

        for (int i = 0; i < rhsArgTypes.length; ++i) {
          if (!I18nConversionCategory.isSubsetOf(lhsArgTypes[i], rhsArgTypes[i])) {
            return false;
          }
        }
        return true;
      } else if ((subKind == I18NINVALIDFORMAT_KIND && superKind == I18NINVALIDFORMAT_KIND)
          || (subKind == I18NFORMATFOR_KIND && superKind == I18NFORMATFOR_KIND)) {
        return Objects.equals(
            treeUtil.getI18nInvalidFormatValue(subAnno),
            treeUtil.getI18nInvalidFormatValue(superAnno));
      }
      throw new TypeSystemError("Unexpected QualifierKinds: %s %s", subKind, superKind);
    }

    @Override
    protected AnnotationMirror leastUpperBoundWithElements(
        AnnotationMirror anno1,
        QualifierKind qualifierKind1,
        AnnotationMirror anno2,
        QualifierKind qualifierKind2,
        QualifierKind lubKind) {
      if (qualifierKind1.isBottom()) {
        return anno2;
      } else if (qualifierKind2.isBottom()) {
        return anno1;
      } else if (qualifierKind1 == I18NFORMAT_KIND && qualifierKind2 == I18NFORMAT_KIND) {
        I18nConversionCategory[] shorterArgTypesList = treeUtil.formatAnnotationToCategories(anno1);
        I18nConversionCategory[] longerArgTypesList = treeUtil.formatAnnotationToCategories(anno2);
        if (shorterArgTypesList.length > longerArgTypesList.length) {
          I18nConversionCategory[] temp = longerArgTypesList;
          longerArgTypesList = shorterArgTypesList;
          shorterArgTypesList = temp;
        }

        // From the manual:
        // It is legal to use a format string with fewer format specifiers
        // than required, but a warning is issued.

        I18nConversionCategory[] resultArgTypes =
            new I18nConversionCategory[longerArgTypesList.length];

        for (int i = 0; i < shorterArgTypesList.length; ++i) {
          resultArgTypes[i] =
              I18nConversionCategory.intersect(shorterArgTypesList[i], longerArgTypesList[i]);
        }
        for (int i = shorterArgTypesList.length; i < longerArgTypesList.length; ++i) {
          resultArgTypes[i] = longerArgTypesList[i];
        }
        return treeUtil.categoriesToFormatAnnotation(resultArgTypes);
      } else if (qualifierKind1 == I18NINVALIDFORMAT_KIND
          && qualifierKind2 == I18NINVALIDFORMAT_KIND) {
        assert !anno1.getElementValues().isEmpty();
        assert !anno1.getElementValues().isEmpty();

        if (AnnotationUtils.areSame(anno1, anno2)) {
          return anno1;
        }

        return treeUtil.stringToInvalidFormatAnnotation(
            "("
                + treeUtil.invalidFormatAnnotationToErrorMessage(anno1)
                + " or "
                + treeUtil.invalidFormatAnnotationToErrorMessage(anno2)
                + ")");
      } else if (qualifierKind1 == I18NFORMATFOR_KIND && AnnotationUtils.areSame(anno1, anno2)) {
        // @I18nFormatFor annotations are unrelated by subtyping, unless they are identical.
        return anno1;
      }

      return I18NUNKNOWNFORMAT;
    }

    @Override
    protected AnnotationMirror greatestLowerBoundWithElements(
        AnnotationMirror anno1,
        QualifierKind qualifierKind1,
        AnnotationMirror anno2,
        QualifierKind qualifierKind2,
        QualifierKind glbKind) {
      if (qualifierKind1.isTop()) {
        return anno2;
      } else if (qualifierKind2.isTop()) {
        return anno1;
      } else if (qualifierKind1 == I18NFORMAT_KIND && qualifierKind2 == I18NFORMAT_KIND) {
        I18nConversionCategory[] anno1ArgTypes = treeUtil.formatAnnotationToCategories(anno1);
        I18nConversionCategory[] anno2ArgTypes = treeUtil.formatAnnotationToCategories(anno2);

        // From the manual:
        // It is legal to use a format string with fewer format specifiers
        // than required, but a warning is issued.
        int length = anno1ArgTypes.length;
        if (anno2ArgTypes.length < length) {
          length = anno2ArgTypes.length;
        }

        I18nConversionCategory[] anno3ArgTypes = new I18nConversionCategory[length];

        for (int i = 0; i < length; ++i) {
          anno3ArgTypes[i] = I18nConversionCategory.union(anno1ArgTypes[i], anno2ArgTypes[i]);
        }
        return treeUtil.categoriesToFormatAnnotation(anno3ArgTypes);
      } else if (qualifierKind1 == I18NINVALIDFORMAT_KIND
          && qualifierKind2 == I18NINVALIDFORMAT_KIND) {

        assert !anno2.getElementValues().isEmpty();

        if (AnnotationUtils.areSame(anno1, anno2)) {
          return anno1;
        }

        return treeUtil.stringToInvalidFormatAnnotation(
            "("
                + treeUtil.invalidFormatAnnotationToErrorMessage(anno1)
                + " and "
                + treeUtil.invalidFormatAnnotationToErrorMessage(anno2)
                + ")");
      } else if (qualifierKind1 == I18NFORMATFOR_KIND && AnnotationUtils.areSame(anno1, anno2)) {
        // @I18nFormatFor annotations are unrelated by subtyping, unless they are identical.
        return anno1;
      }

      return I18NFORMATBOTTOM;
    }
  }
}
