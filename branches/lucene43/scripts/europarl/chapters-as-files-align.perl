#!/usr/bin/perl -w
# Wrapper for europarl aligner to put chapter units in separate
# bilingual file pairs.
# Use with europarl data from http://www.statmt.org/europarl/
# This is not production (or even good) code! You will need to make sure that
# i. You have downloaded the Europarl data;
# ii. You have run the main sentence-align-corpus.perl utility;
# iii. If all your data is then in the right place, you should be able to run
#      chapters-as-files-align.perl LANG1 LANG2 to create chapter aligned files.
#
# authors: Dominic Widdows, for the semanticvectors project.

my $dir = "aligned";
my $outdir = "aligned-chapters";

my ($l1,$l2) = @ARGV;
$dir .= "/" . $l1 . "-" . $l2;
die unless -e "$dir/$l1";
die unless -e "$dir/$l2";

`mkdir -p $outdir/$l1-$l2/$l1`;
`mkdir -p $outdir/$l1-$l2/$l2`;

iterate_files();

# gets file from end of path.
sub get_file_from_path {
  my @path_elts = split("/", $_[0]);
  my $file = pop(@path_elts);
  return $file;
}

# gets chapter ids from a file.
sub count_chapters {
  open(FILE, $_[0]);
  my $chapters = 0;
  while (my $line = <FILE>) {
    if ($line =~ m/<CHAPTER ID=(\d+)/) {
      ++$chapters;
    }
  }
  return $chapters;
}

sub extract_from_file_pair {
  if (count_chapters($_[0]) != count_chapters($_[1])) {
    print "Failed chapters equal test for: " . get_file_from_path($_[0]) . "\n";
  } else {
    print "Chapters should align for: " . get_file_from_path($_[0]) . "\n";
  }
  open(FILE1, $_[0]);
  open(FILE2, $_[1]);
  my $chapters = count_chapters($_[0]);
  my $line;
  my $ch1;
  my $ch2;
  my $written = 0;

  while(1) {
    while($line = <FILE1>) {
      if ($line =~ m/<CHAPTER ID=(\d+)/) {
	$ch1 = $1;
	last;
      }
    } 
    if (not $line) { last; }

    while($line = <FILE2>) {
      if ($line =~ m/<CHAPTER ID=(\d+)/) {
	$ch2 = $1;
	last;
      }
    }
    if (not $line) { last; }

    if ($ch1 == $ch2) {
      $fn = get_file_from_path($_[0]);
      $fn =~ s/\.txt$//;
      $fn .= "-ch-$ch1.txt";
      print "Writing files: " . $fn . "\n";
      open(OUTPUT1, "> $outdir/$l1-$l2/$l1/$fn");
      while($line = <FILE1>) {
	if ($line =~ m/<CHAPTER/) {
	  # go back a line
	  seek (FILE1, -length($line), 1);
	  last;
	} else {
	  print OUTPUT1 $line;
	}
      }
      close OUTPUT1;

      open(OUTPUT2, "> $outdir/$l1-$l2/$l2/$fn");
      while($line = <FILE2>) {
	if ($line =~ m/<CHAPTER/) {
	  # go back a line
	  seek (FILE2, -length($line), 1);
	  last;
	} else {
	  print OUTPUT2 $line;
	}
      }
      close OUTPUT2;
      ++$written;
    }
  }
  if ($written != $chapters) {
    print "Expected $chapters chapters ... got $written.\n";
  }
}

sub iterate_files {
  my @files1 = <$dir/$l1/*>;
  my @files2 = <$dir/$l2/*>;
  if (scalar @files1 != scalar @files2) {
    die "Unequal numbers of input files: @files1 @files2\n";
  }
  for (my $i = 0; $i < scalar(@files1); ++$i) {
    if (get_file_from_path($files1[$i]) eq get_file_from_path($files2[$i])) {
      &extract_from_file_pair($files1[$i], $files2[$i]);
    } else {
      print get_file_from_path($files1[$i]) . " and " .
	get_file_from_path($files1[$i]) . " don't align!\n";
    }
  }
}
