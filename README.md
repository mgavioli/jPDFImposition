## jPDFImposition

A Java command-line application to apply an imposition to one or several PDF documents.

It currently supports two types of impositions:
- **booklet**: groups of sheets folded in the middle; the number of sheets per group can be configured.
- **large-size impositions**: large-sized sheets to be individually folded, bound and trimmed; three standard impositions are supported: in 4°, in 8° and in 16°, each in two folding types.

The application reads one or more source PDF documents, with individual pages in sequential order and generates a destination PDF document with pages (of larger size) representing the front and back print forme of each sheet.

#### Download

A ZIP file with the latest runnable .jar and all its dependencies can be downlaoded from the [Releases page](https://github.com/mgavioli/jPDFImposition/releases).

The documentation is available in the [*doc* folder listing](https://github.com/mgavioli/jPDFImposition/tree/master/doc)

#### Dependencies

The project has only one dependency:
- **jPOD**, a free, open source, PDF manipulation library which can be downloaded [here](http://opensource.intarsys.de/home/en/index.php?n=JPod.HomePage).

#### Compiling from sources

To compile the package:

1. Download the jPOD library.
2. Expand it anywhere in your disk.
3. Within your IDE of choice, add to your project each `.java` file in the jPDFImposition `src/` folder.
3. Add to your project build path at least `iscwt.jar`, `isrt.jar` and `jPod.jar` in the jPOD `lib/` folder.
4. Compile...

#### Disclaimer

This project is still rather experimental and scantily tested. It is developed under Eclipse, but it should be easily portable to other IDE's.

In setting your project up, many things can go wrong: feedback is welcome to improve the setup.

The whole package is supplied **as is**, without any warranty of whatever kind: use at your own risk!
